package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.scriptsafe.merge.MergeException;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptCallNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptEvalNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraphProxy;

public class ScriptDatasetLoader {

	private static class PendingEdges<NodeType extends ScriptNode, TargetType> {
		final NodeType fromNode;
		final TargetType target;

		PendingEdges(NodeType fromNode, TargetType target) {
			this.fromNode = fromNode;
			this.target = target;
		}
	}

	private final List<PendingEdges<ScriptCallNode, List<Long>>> pendingCalls = new ArrayList<PendingEdges<ScriptCallNode, List<Long>>>();
	private final List<PendingEdges<ScriptEvalNode, List<Integer>>> pendingEvals = new ArrayList<PendingEdges<ScriptEvalNode, List<Integer>>>();
	private final List<PendingEdges<ScriptBranchNode, Integer>> pendingBranches = new ArrayList<PendingEdges<ScriptBranchNode, Integer>>();
	private final List<ScriptNode> calls = new ArrayList<ScriptNode>();

	private LittleEndianInputStream in;

	public void loadDataset(ScriptDatasetFile dataset, ScriptFlowGraph graph) throws IOException {
		in = new LittleEndianInputStream(dataset.file);

		in.readInt(); // skip hashtable pointer
		int routineCount = in.readInt();
		int dynamicRoutineCount = in.readInt();

		for (int i = 0; i < routineCount; i++)
			graph.addRoutine(loadNextRoutine());
		for (int i = 0; i < dynamicRoutineCount; i++)
			graph.appendDynamicRoutine(new ScriptRoutineGraphProxy(loadNextRoutine()));

		for (PendingEdges<ScriptCallNode, List<Long>> pendingCall : pendingCalls) {
			for (Long targetId : pendingCall.target) {
				ScriptRoutineGraph target = graph.getRoutine(targetId);
				if (target == null)
					throw new MergeException("Call to unknown routine 0x%x", targetId);

				pendingCall.fromNode.addStaticTarget(target);
			}
		}
		for (PendingEdges<ScriptEvalNode, List<Integer>> pendingEval : pendingEvals) {
			for (Integer targetId : pendingEval.target) {
				ScriptRoutineGraphProxy target = graph.getDynamicRoutineProxy(targetId);
				if (target == null)
					throw new MergeException("Call to unknown dynamic routine %d", targetId);

				pendingEval.fromNode.addTarget(target);
			}
		}

		in.close();
	}

	private ScriptRoutineGraph loadNextRoutine() throws IOException {
		int unitHash = in.readInt();
		int routineHash = in.readInt();
		int dynamicRoutineId, dynamicRoutineCount;
		ScriptRoutineGraph routine = new ScriptRoutineGraph(unitHash, routineHash);

		int nodeCount = in.readInt();
		for (int i = 0; i < nodeCount; i++) {
			int nodeId = in.readInt();
			int opcode = nodeId & 0xff;
			int typeOrdinal = (nodeId >> 8);
			ScriptNode.Type type = ScriptNode.Type.values()[typeOrdinal];
			int target = in.readInt();
			switch (type) {
				case NORMAL:
					routine.addNode(new ScriptNode(opcode, i));
					break;
				case BRANCH:
					ScriptBranchNode branch = new ScriptBranchNode(opcode, i);
					pendingBranches.add(new PendingEdges<ScriptBranchNode, Integer>(branch, target));
					routine.addNode(branch);
					break;
				case CALL:
					ScriptCallNode call = new ScriptCallNode(opcode, i);
					calls.add(call); // use list seequence instead of `target` pointer
					routine.addNode(call);
					break;
				case EVAL:
					ScriptEvalNode eval = new ScriptEvalNode(opcode, i);
					calls.add(eval);// use list seequence instead of `target` pointer
					routine.addNode(eval);
					break;
			}
		}

		for (PendingEdges<ScriptBranchNode, Integer> pendingBranch : pendingBranches)
			pendingBranch.fromNode.setTarget(routine.getNode(pendingBranch.target));

		for (ScriptNode call : calls) {
			switch (call.type) {
				case CALL: {
					int callCount = in.readInt();
					PendingEdges<ScriptCallNode, List<Long>> pendingCall = new PendingEdges<ScriptCallNode, List<Long>>(
							(ScriptCallNode) call, new ArrayList<Long>());
					for (int i = 0; i < callCount; i++) {
						unitHash = in.readInt();
						routineHash = in.readInt();
						pendingCall.target.add(ScriptRoutineGraph.constructId(unitHash, routineHash));
					}
					pendingCalls.add(pendingCall);

				}
					break;
				case EVAL: {
					dynamicRoutineCount = in.readInt();
					PendingEdges<ScriptEvalNode, List<Integer>> pendingEval = new PendingEdges<ScriptEvalNode, List<Integer>>(
							(ScriptEvalNode) call, new ArrayList<Integer>());
					for (int i = 0; i < dynamicRoutineCount; i++) {
						dynamicRoutineId = in.readInt();
						pendingEval.target.add(dynamicRoutineId);
					}
					pendingEvals.add(pendingEval);

				}
					break;
			}
		}

		calls.clear();
		pendingBranches.clear();

		return routine;
	}
}
