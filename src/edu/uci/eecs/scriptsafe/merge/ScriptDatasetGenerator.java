package edu.uci.eecs.scriptsafe.merge;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge.Type;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineExceptionEdge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptDatasetGenerator {

	public interface DataSource {
		int getDynamicRoutineCount();

		int getStaticRoutineCount();

		Iterable<ScriptRoutineGraph> getDynamicRoutines();

		Iterable<ScriptRoutineGraph> getStaticRoutines();

		int getOutgoingEdgeCount(ScriptNode node);

		Iterable<RoutineEdge> getOutgoingEdges(ScriptNode node);
	}

	private static class Hashtable {
		HashtableConfiguration configuration;
		HashtableEntry table[];

		public Hashtable(HashtableConfiguration configuration) {
			this.configuration = configuration;
			table = new HashtableEntry[configuration.size];
		}

		HashtableEntry getEntry(long routineId) {
			int key = getKey(routineId);
			HashtableEntry entry = table[key];

			while (entry != null) {
				if (entry.routineId == routineId)
					return entry;
			}
			return null;
		}

		void putEntry(long routineId, int dataOffset) {
			int key = getKey(routineId);
			HashtableEntry entry = table[key];
			if (entry == null) {
				table[key] = new HashtableEntry(routineId, dataOffset);
			} else {
				while (entry.next != null)
					entry = entry.next;

				entry.next = new HashtableEntry(routineId, dataOffset);
			}
		}

		private int getKey(long routineId) {
			return (((int) (routineId >> 0x20)) ^ ((int) (routineId & 0xffffffffL))) & configuration.mask;
		}
	}

	private static class HashtableConfiguration {
		int bits = 1, size = 1, mask = 0;

		void configure(int entryCount) {
			while ((entryCount / (float) size) > 0.7f) {
				size <<= 1;
				bits++;
			}
			mask = (size - 1);
		}
	}

	private static class HashtableEntry {
		final long routineId;
		HashtableEntry next = null;
		final int dataOffset;
		int chainOffset;

		public HashtableEntry(long routineId, int dataOffset) {
			this.routineId = routineId;
			this.dataOffset = dataOffset;
		}
	}

	private static final int HASHTABLE_ENTRY_SIZE = 4;

	private final DataSource dataSource;
	private final File outputFile;
	private final LittleEndianOutputStream out;

	private final HashtableConfiguration hashtableConfiguration;
	private final Hashtable hashtable;
	private final List<Integer> dynamicRoutineOffsets = new ArrayList<Integer>();

	private int filePtr = 0;
	private int callTargetPtr;
	private int hashtableStart;

	public ScriptDatasetGenerator(DataSource dataSource, File outputFile) throws IOException {
		this.dataSource = dataSource;
		this.outputFile = outputFile;
		out = new LittleEndianOutputStream(outputFile);

		hashtableConfiguration = new HashtableConfiguration();
		hashtableConfiguration.configure(dataSource.getStaticRoutineCount());
		hashtable = new Hashtable(hashtableConfiguration);
	}

	public void generateDataset() throws IOException {
		out.writeInt(0); // placeholder for hashtableStart
		out.writeInt(dataSource.getStaticRoutineCount());
		out.writeInt(dataSource.getDynamicRoutineCount());
		filePtr += 3;

		writeRoutines();
		writeRoutineHashtableChains();
		writeRoutineHashtable();
		writeDynamicRoutineList();

		out.flush();
		out.close();

		byte buffer[] = new byte[4];
		buffer[0] = (byte) hashtableStart;
		buffer[1] = (byte) (hashtableStart >> 8);
		buffer[2] = (byte) (hashtableStart >> 0x10);
		buffer[3] = (byte) (hashtableStart >> 0x18);
		RandomAccessFile insertOut = new RandomAccessFile(outputFile, "rw");
		insertOut.seek(0);
		insertOut.write(buffer);
		insertOut.close();
	}

	private void writeDynamicRoutineList() throws IOException {
		out.writeInt(dynamicRoutineOffsets.size());
		for (Integer dynamicRoutineOffset : dynamicRoutineOffsets) {
			out.writeInt(dynamicRoutineOffset);
		}
		filePtr += (1 + dynamicRoutineOffsets.size());
	}

	private void writeRoutineHashtable() throws IOException {
		hashtableStart = filePtr;
		out.writeInt(hashtableConfiguration.mask);
		for (int i = 0; i < hashtableConfiguration.size; i++) {
			if (hashtable.table[i] == null)
				out.writeInt(0);
			else
				out.writeInt(hashtable.table[i].chainOffset);
		}
		filePtr += (1 + hashtableConfiguration.size);
	}

	private void writeRoutineHashtableChains() throws IOException {
		for (int i = 0; i < hashtableConfiguration.size; i++) {
			HashtableEntry entry = hashtable.table[i];
			if (entry != null) {
				entry.chainOffset = filePtr;
				do {
					out.writeInt(entry.dataOffset);
					filePtr++;
					entry = entry.next;
				} while (entry != null);
				out.writeInt(0); // null-terminate the chain
				filePtr++;
			}
		}
	}

	private void writeRoutineData(ScriptRoutineGraph routine) throws IOException {
		List<ScriptNode> calls = new ArrayList<ScriptNode>();
		int targetIndex;
		out.writeInt(routine.unitHash);
		out.writeInt(routine.routineHash);
		out.writeInt(routine.getNodeCount());
		callTargetPtr = filePtr + (3 + (routine.getNodeCount() * 2));

		for (int i = 0; i < routine.getNodeCount(); i++) {
			ScriptNode node = routine.getNode(i);
			int nodeId = (node.type.ordinal() << 8) | node.opcode;
			out.writeInt(nodeId);

			switch (node.type) {
				case NORMAL:
					out.writeInt(0);
					break;
				case BRANCH:
					targetIndex = ((ScriptBranchNode) node).getTargetIndex(routine.id);
					out.writeInt(targetIndex);
					break;
				case CALL:
				case EVAL:
					calls.add(node);
					out.writeInt(callTargetPtr);
					callTargetPtr += (1 + (2 * dataSource.getOutgoingEdgeCount(node)));
					break;
			}
		}
		filePtr += (3 + (routine.getNodeCount() * 2));

		for (ScriptNode call : calls) {
			switch (call.type) {
				case CALL: {
					out.writeInt(dataSource.getOutgoingEdgeCount(call));
					for (RoutineEdge target : dataSource.getOutgoingEdges(call)) {
						out.writeLong(target.getToRoutineId());
						if (target.getEntryType() == Type.CALL)
							targetIndex = 0; // routine entry point
						else
							targetIndex = ((RoutineExceptionEdge) target).getToRoutineIndex();
						targetIndex |= (target.getUserLevel() << 26); // sign?
						out.writeInt(targetIndex);
					}
					filePtr += (1 + (3 * dataSource.getOutgoingEdgeCount(call)));
				}
					break;
				case EVAL: {
					out.writeInt(dataSource.getOutgoingEdgeCount(call));
					for (RoutineEdge target : dataSource.getOutgoingEdges(call)) {
						out.writeInt(ScriptRoutineGraph.getDynamicRoutineId(target.getToRoutineId()));
						if (target.getEntryType() == Type.CALL)
							targetIndex = 0; // routine entry point
						else
							targetIndex = ((RoutineExceptionEdge) target).getToRoutineIndex();
						targetIndex |= (target.getUserLevel() << 26); // sign?
						out.writeInt(targetIndex);
					}
					filePtr += (1 + (2 * dataSource.getOutgoingEdgeCount(call)));
				}
			}
		}
	}

	private void writeRoutines() throws IOException {
		callTargetPtr = 0;
		dynamicRoutineOffsets.clear();
		for (ScriptRoutineGraph routine : dataSource.getStaticRoutines()) {
			hashtable.putEntry(routine.id, filePtr);
			writeRoutineData(routine);
		}
		for (ScriptRoutineGraph routine : dataSource.getDynamicRoutines()) {
			dynamicRoutineOffsets.add(filePtr);
			writeRoutineData(routine);
		}
	}
}
