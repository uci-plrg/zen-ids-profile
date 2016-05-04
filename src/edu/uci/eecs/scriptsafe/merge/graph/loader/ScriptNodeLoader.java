package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;
import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;

public class ScriptNodeLoader {

	public interface LoadContext {
		ScriptRoutineGraph createRoutine(int routineHash);

		ScriptRoutineGraph getRoutine(int routineHash);
	}

	private LoadContext loadContext;

	public ScriptNodeLoader() {
	}

	public ScriptNodeLoader(LoadContext loadContext) {
		this.loadContext = loadContext;
	}

	public void setLoadContext(LoadContext loadContext) {
		this.loadContext = loadContext;
	}

	public void loadNodes(File nodeFile) throws IOException {
		int routineHash, opcodeField, opcode, extendedValue, lineNumber, nodeIndex = 0;
		ScriptNode.Type type;
		ScriptNode node, previousNode = null;
		ScriptRoutineGraph routine = null;
		LittleEndianInputStream input = new LittleEndianInputStream(nodeFile);

		while (input.ready(0xc)) {
			routineHash = input.readInt();

			if (routine == null || routine.hash != routineHash) {
				previousNode = null;
				routine = loadContext.getRoutine(routineHash);
			}

			if (routine == null) {
				Log.message("Create routine %x", routineHash);
				routine = loadContext.createRoutine(routineHash);
			}

			opcodeField = input.readInt();
			opcode = opcodeField & 0xff;
			extendedValue = (opcodeField >> 8) & 0xff;
			lineNumber = opcodeField >> 0x10;
			type = ScriptNode.identifyType(opcode, extendedValue);

			// parse out extended value for include/eval nodes
			nodeIndex = input.readInt();
			node = createNode(routineHash, opcode, type, lineNumber, nodeIndex);
			if (nodeIndex > routine.getNodeCount()) {
				Log.warn("Skipping node %d with disjoint index %d", routine.getNodeCount(), nodeIndex);
				continue;
			}
			if (previousNode != null)
				previousNode.setNext(node);
			previousNode = node;

			Log.message("%s: @%d#%d Opcode 0x%x (%x) [%s]", getClass().getSimpleName(), nodeIndex, lineNumber, opcode,
					routineHash, node.type);
			if (ScriptMergeWatchList.watch(routineHash)) {
				Log.log("%s: @%d Opcode 0x%x (%x) [%s]", getClass().getSimpleName(), nodeIndex, opcode, routineHash,
						node.type);
			}

			routine.addNode(node);
		}

		if (input.ready())
			Log.error("Input file " + nodeFile.getAbsolutePath() + " has trailing data!");
		input.close();
	}

	private ScriptNode createNode(int routineHash, int opcode, ScriptNode.Type type, int lineNumber, int index) {
		if (type == Type.BRANCH) {
			int userLevel = (index >>> 26);
			index = (index & 0x3ffffff);
			return new ScriptBranchNode(routineHash, opcode, index, lineNumber, userLevel);
		} else {
			return new ScriptNode(routineHash, type, opcode, lineNumber, index);
		}
	}
}
