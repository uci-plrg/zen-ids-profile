package edu.uci.plrg.cfi.php.merge;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import edu.uci.plrg.cfi.common.io.LittleEndianOutputStream;
import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.php.merge.graph.RoutineEdge;
import edu.uci.plrg.cfi.php.merge.graph.RoutineEdge.Type;
import edu.uci.plrg.cfi.php.merge.graph.RoutineExceptionEdge;
import edu.uci.plrg.cfi.php.merge.graph.RoutineId;
import edu.uci.plrg.cfi.php.merge.graph.ScriptBranchNode;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode.OpcodeTargetType;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode.TypeFlag;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;

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

		HashtableEntry getEntry(int routineHash) {
			int key = getKey(routineHash);
			HashtableEntry entry = table[key];

			while (entry != null) {
				if (entry.routineHash == routineHash)
					return entry;
			}
			return null;
		}

		void putEntry(int routineHash, int dataOffset) {
			int key = getKey(routineHash);
			HashtableEntry entry = table[key];
			if (entry == null) {
				table[key] = new HashtableEntry(routineHash, dataOffset);
			} else {
				while (entry.next != null)
					entry = entry.next;

				entry.next = new HashtableEntry(routineHash, dataOffset);
			}
		}

		private int getKey(int routineHash) {
			return routineHash & configuration.mask;
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
		final int routineHash;
		HashtableEntry next = null;
		final int dataOffset;
		int chainOffset;

		public HashtableEntry(int routineHash, int dataOffset) {
			this.routineHash = routineHash;
			this.dataOffset = dataOffset;
		}
	}

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

	private int getEdgeSpace(ScriptNode node) {
		return (1 + (2 * dataSource.getOutgoingEdgeCount(node)));
	}

	private void writeRoutineData(ScriptRoutineGraph routine) throws IOException {
		List<ScriptNode> calls = new ArrayList<ScriptNode>();
		int targetIndexField = 0, callTargetsField = 0;
		out.writeInt(routine.hash);
		out.writeInt(routine.getNodeCount());
		int nodeSpace = (2/* accounts for the previous 2 lines */+ (routine.getNodeCount() * 3));
		callTargetPtr = filePtr + nodeSpace;

		for (int i = 0; i < routine.getNodeCount(); i++) {
			ScriptNode node = routine.getNode(i);
			int nodeId = (node.lineNumber << 0x10) | (TypeFlag.encode(node.typeFlags) << 8) | node.opcode;
			out.writeInt(nodeId);

			targetIndexField = 0;
			if (node.typeFlags.contains(TypeFlag.BRANCH)) {
				ScriptBranchNode branch = (ScriptBranchNode) node;
				targetIndexField = branch.getTargetIndex();
				if (targetIndexField == ScriptBranchNode.UNASSIGNED_BRANCH_TARGET_ID
						&& ScriptNode.Opcode.forCode(branch.opcode).targetType == OpcodeTargetType.REQUIRED)
					targetIndexField = node.index + 1; // hack for silly JMPZ +1
			}
			targetIndexField |= (node.getNodeUserLevel() << 26);
			if (node.getNodeUserLevel() < 0x3f) {
				Log.message("%d at %04d(L%04d) in %s", node.getNodeUserLevel(), node.index, node.lineNumber,
						routine.id.name);
			}
			out.writeInt(targetIndexField);

			callTargetsField = 0;
			if (node.typeFlags.contains(TypeFlag.EVAL) || node.typeFlags.contains(TypeFlag.CALL)) {
				int callTargetCount = 0, exceptionTargetCount = 0;
				calls.add(node);
				callTargetsField = callTargetPtr;
				for (RoutineEdge target : dataSource.getOutgoingEdges(node)) {
					if (target.getEntryType() == Type.CALL)
						callTargetCount++;
					else
						exceptionTargetCount++;
				}
				if (ScriptMergeWatchList.watchAny(routine.hash, node.index)) {
					Log.log("Dataset generator reserved %d call targets and %d exception targets for 0x%x %d at 0x%x",
							callTargetCount, exceptionTargetCount, routine.hash, node.index, callTargetPtr);
				}
				callTargetPtr += getEdgeSpace(node);
			} else if (dataSource.getOutgoingEdgeCount(node) > 0) {
				Log.error("Error: skipping %d outgoing edges from opcode 0x%x at 0x%x %d",
						dataSource.getOutgoingEdgeCount(node), node.opcode, routine.hash, node.index);
				for (RoutineEdge target : dataSource.getOutgoingEdges(node)) {
					RoutineId targetId = RoutineId.Cache.INSTANCE.getId(target.getToRoutineHash());
					String edgeType = (target.getEntryType() == Type.CALL) ? "Call" : "Exception";
                    int toIndex = (target.getEntryType() == Type.CALL) ? 0 : ((RoutineExceptionEdge) target).getToRoutineIndex();
					Log.error("\t%s to op %d of %s", edgeType, toIndex, targetId.id);
				}
			}
			out.writeInt(callTargetsField);
		}
		filePtr += nodeSpace;

		for (ScriptNode call : calls) {
			if (call.typeFlags.contains(TypeFlag.CALL)) {
				out.writeInt(dataSource.getOutgoingEdgeCount(call));
				for (RoutineEdge target : dataSource.getOutgoingEdges(call)) {
					out.writeInt(target.getToRoutineHash());
					if (target.getEntryType() == Type.CALL)
						targetIndexField = 0; // routine entry point
					else
						targetIndexField = ((RoutineExceptionEdge) target).getToRoutineIndex();
					targetIndexField |= (target.getUserLevel() << 26); // sign?
					out.writeInt(targetIndexField);

					if (ScriptMergeWatchList.watchAny(routine.hash, call.index)
							|| ScriptMergeWatchList.watch(target.getToRoutineHash())) {
						Log.log("Wrote edge [%s -> %s] with user level %d as [ -> 0x%x 0x%x] at offset 0x%x",
								target.printFromNode(), target.printToNode(), target.getUserLevel(),
								target.getToRoutineHash(), targetIndexField, filePtr);
					}
				}
				filePtr += (1 + (2 * dataSource.getOutgoingEdgeCount(call)));
			} else if (call.typeFlags.contains(TypeFlag.EVAL)) {
				out.writeInt(dataSource.getOutgoingEdgeCount(call));
				for (RoutineEdge target : dataSource.getOutgoingEdges(call)) {
					out.writeInt(ScriptRoutineGraph.getDynamicRoutineIndex(target.getToRoutineHash()));
					if (target.getEntryType() == Type.CALL)
						targetIndexField = 0; // routine entry point
					else
						targetIndexField = ((RoutineExceptionEdge) target).getToRoutineIndex();
					targetIndexField |= (target.getUserLevel() << 26); // sign?
					out.writeInt(targetIndexField);
				}
				filePtr += (1 + (2 * dataSource.getOutgoingEdgeCount(call)));
			}
		}
	}

	private void writeRoutines() throws IOException {
		callTargetPtr = 0;
		dynamicRoutineOffsets.clear();
		for (ScriptRoutineGraph routine : dataSource.getStaticRoutines()) {
			hashtable.putEntry(routine.hash, filePtr);
			writeRoutineData(routine);
		}
		for (ScriptRoutineGraph routine : dataSource.getDynamicRoutines()) {
			dynamicRoutineOffsets.add(filePtr);
			writeRoutineData(routine);
		}
	}
}
