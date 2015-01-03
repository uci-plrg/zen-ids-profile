package edu.uci.eecs.scriptsafe.merge;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptCallNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptEvalNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraphProxy;

public class ScriptDatasetGenerator {

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
			return (((int) (routineId >> 16)) ^ ((int) (routineId & 0xffffffffL))) & configuration.mask;
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

	private final ScriptFlowGraph graph;
	private final File outputFile;
	private final LittleEndianOutputStream out;

	private final HashtableConfiguration hashtableConfiguration;
	private final Hashtable hashtable;
	private final List<Integer> evalOffsets = new ArrayList<Integer>();

	private int filePtr = 0;
	private int callTargetPtr;
	private int hashtableStart;

	public ScriptDatasetGenerator(ScriptFlowGraph graph, File outputFile) throws IOException {
		this.graph = graph;
		this.outputFile = outputFile;
		out = new LittleEndianOutputStream(outputFile);

		hashtableConfiguration = new HashtableConfiguration();
		hashtableConfiguration.configure(graph.getRoutineCount());
		hashtable = new Hashtable(hashtableConfiguration);
	}

	public void generateDataset() throws IOException {
		out.writeInt(0); // placeholder for hashtableStart
		out.writeInt(graph.getRoutineCount());
		filePtr += 2;

		writeRoutines();
		writeRoutineHashtableChains();
		writeRoutineHashtable();
		writeEvalList();

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

	private void writeEvalList() throws IOException {
		out.writeInt(evalOffsets.size());
		for (Integer evalOffset : evalOffsets) {
			out.writeInt(evalOffset);
		}
		filePtr += (1 + evalOffsets.size());
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
		out.writeInt(routine.unitHash);
		out.writeInt(routine.routineHash);
		out.writeInt(routine.getNodeCount());
		callTargetPtr = filePtr + (3 + (routine.getNodeCount() * 2));

		for (int i = 0; i < routine.getNodeCount(); i++) {
			ScriptNode node = routine.getNode(i);
			out.writeInt(node.opcode);

			switch (node.type) {
				case NORMAL:
					out.writeInt(0);
					break;
				case BRANCH:
					out.writeInt(((ScriptBranchNode) node).getTarget().index);
					break;
				case CALL:
					ScriptCallNode call = (ScriptCallNode) node;
					calls.add(call);
					out.writeInt(callTargetPtr);
					callTargetPtr += (1 + (2 * call.getTargetCount()));
					break;
				case EVAL:
					ScriptEvalNode eval = (ScriptEvalNode) node;
					calls.add(eval);
					out.writeInt(callTargetPtr);
					callTargetPtr += (1 + eval.getTargetCount());

					break;
			}
		}
		filePtr += (3 + (routine.getNodeCount() * 2));

		for (ScriptNode callNode : calls) {
			switch (callNode.type) {
				case CALL: {
					ScriptCallNode call = (ScriptCallNode) callNode;
					out.writeInt(call.getTargetCount());
					for (ScriptRoutineGraph target : call.getTargets()) {
						out.writeInt(target.unitHash);
						out.writeInt(target.routineHash);
					}
					filePtr += (1 + (2 * call.getTargetCount()));
				}
					break;
				case EVAL: {
					ScriptEvalNode eval = (ScriptEvalNode) callNode;
					for (ScriptRoutineGraphProxy target : eval.getTargets()) {
						out.writeInt(target.getEvalId());
					}
					out.writeInt(0); // null terminator
					filePtr += (1 + eval.getTargetCount());
				}
			}
		}
	}

	private void writeRoutines() throws IOException {
		callTargetPtr = 0;
		evalOffsets.clear();
		for (ScriptRoutineGraph routine : graph.getRoutines()) {
			hashtable.putEntry(routine.id, filePtr);
			writeRoutineData(routine);
		}
		for (ScriptRoutineGraphProxy evalProxy : graph.getEvalProxies()) {
			evalOffsets.add(filePtr);
			writeRoutineData(evalProxy.getTarget());
		}
	}
}
