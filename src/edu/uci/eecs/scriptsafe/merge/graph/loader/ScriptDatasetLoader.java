package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;

public class ScriptDatasetLoader {

	private static class PendingEdges<NodeType extends ScriptNode, TargetType> {
		final long fromRoutineId;
		final NodeType fromNode;
		final TargetType target;

		PendingEdges(long fromRoutineId, NodeType fromNode, TargetType target) {
			this.fromRoutineId = fromRoutineId;
			this.fromNode = fromNode;
			this.target = target;
		}
	}

	private final List<PendingEdges<ScriptBranchNode, Integer>> pendingBranches = new ArrayList<PendingEdges<ScriptBranchNode, Integer>>();
	private final List<ScriptNode> calls = new ArrayList<ScriptNode>();

	private LittleEndianInputStream in;

	public void loadDataset(ScriptDatasetFile dataset, ScriptFlowGraph graph) throws IOException {
		in = new LittleEndianInputStream(dataset.file);

		in.readInt(); // skip hashtable pointer
		int routineCount = in.readInt();
		int dynamicRoutineCount = in.readInt();

		for (int i = 0; i < routineCount; i++)
			graph.addRoutine(loadNextRoutine(graph));
		for (int i = 0; i < dynamicRoutineCount; i++)
			graph.appendDynamicRoutine(loadNextRoutine(graph));

		in.close();
	}

	private ScriptRoutineGraph loadNextRoutine(ScriptFlowGraph graph) throws IOException {
		int unitHash = in.readInt();
		int routineHash = in.readInt();
		int dynamicRoutineId, dynamicRoutineCount, targetNodeIndex;
		ScriptRoutineGraph routine = new ScriptRoutineGraph(unitHash, routineHash);

		int nodeCount = in.readInt();
		for (int i = 0; i < nodeCount; i++) {
			int nodeId = in.readInt();
			int opcode = nodeId & 0xff;
			int typeOrdinal = (nodeId >> 8);
			ScriptNode.Type type = ScriptNode.Type.values()[typeOrdinal];
			int target = in.readInt();
			if (type == Type.BRANCH) {
				ScriptBranchNode branch = new ScriptBranchNode(opcode, i);
				pendingBranches.add(new PendingEdges<ScriptBranchNode, Integer>(routineHash, branch, target));
				routine.addNode(branch);
			} else {
				ScriptNode call = new ScriptNode(type, opcode, i);
				calls.add(call); // use list seequence instead of `target` pointer
				routine.addNode(call);
			}
		}

		for (PendingEdges<ScriptBranchNode, Integer> pendingBranch : pendingBranches) {
			if (ScriptNode.Opcode.forCode(pendingBranch.fromNode.opcode).isDynamic)
				pendingBranch.fromNode.setTarget(null);
			else
				pendingBranch.fromNode.setTarget(routine.getNode(pendingBranch.target));
		}

		for (ScriptNode call : calls) {
			switch (call.type) {
				case CALL: {
					int callCount = in.readInt();
					for (int i = 0; i < callCount; i++) {
						unitHash = in.readInt();
						routineHash = in.readInt();
						targetNodeIndex = in.readInt();
						graph.graphEdgeSet.addCallEdge(routine.id, call,
								ScriptRoutineGraph.constructId(unitHash, routineHash));
					}
				}
					break;
				case EVAL: {
					dynamicRoutineCount = in.readInt();
					for (int i = 0; i < dynamicRoutineCount; i++) {
						dynamicRoutineId = in.readInt();
						targetNodeIndex = in.readInt();
						graph.graphEdgeSet.addExceptionEdge(routine.id, call,
								ScriptRoutineGraph.constructId(ScriptRoutineGraph.DYNAMIC_UNIT_HASH, dynamicRoutineId),
								targetNodeIndex);
					}
				}
					break;
			}
		}

		calls.clear();
		pendingBranches.clear();

		return routine;
	}
}
