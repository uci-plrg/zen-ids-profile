package edu.uci.eecs.scriptsafe.merge;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptCallNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptDatasetGenerator {

	private final ScriptFlowGraph graph;
	private final List<ScriptBranchNode> branches = new ArrayList<ScriptBranchNode>();
	private final List<ScriptCallNode> calls = new ArrayList<ScriptCallNode>();
	private final LittleEndianOutputStream out;

	public ScriptDatasetGenerator(ScriptFlowGraph graph, File outputFile) throws IOException {
		this.graph = graph;
		out = new LittleEndianOutputStream(outputFile);
	}

	public void generateDataset() throws IOException {
		writeRoutineData();

		out.flush();
		out.close();
	}

	private void writeRoutineData() throws IOException {
		for (ScriptRoutineGraph routine : graph.getRoutines()) {
			out.writeInt(routine.unitHash);
			out.writeInt(routine.routineHash);
			out.writeInt(routine.getNodeCount());

			for (int i = 0; i < routine.getNodeCount(); i++) {
				ScriptNode node = routine.getNode(i);
				out.writeInt(node.opcode);

				switch (node.type) {
					case BRANCH:
						branches.add((ScriptBranchNode) node);
						break;
					case CALL:
						calls.add((ScriptCallNode) node);
						break;
				}
			}

			out.writeInt(branches.size());
			for (ScriptBranchNode branch : branches) {
				out.writeInt(branch.index);
				out.writeInt(branch.getTarget().index);
			}
			for (ScriptCallNode call : calls) {
				out.writeInt(call.index);
				out.writeInt(call.getTargetCount());
				for (ScriptRoutineGraph target : call.getTargets()) {
					out.writeInt(target.unitHash);
					out.writeInt(target.routineHash);
				}
			}
		}
	}
}
