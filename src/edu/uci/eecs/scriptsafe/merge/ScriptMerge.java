package edu.uci.eecs.scriptsafe.merge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptCallNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptEvalNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptGraphCloner;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptMergeTarget;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraphProxy;

public class ScriptMerge {

	private final Map<ScriptCallNode, ScriptCallNode> callNodeMerges = new HashMap<ScriptCallNode, ScriptCallNode>();

	final ScriptFlowGraph left;
	final ScriptMergeTarget target;

	private int evalId = 0;

	public ScriptMerge(ScriptFlowGraph left, ScriptFlowGraph right) {
		this.left = left;

		ScriptGraphCloner cloner = new ScriptGraphCloner();
		target = cloner.copyToMergeTarget(right);
	}

	public ScriptFlowGraph merge() {
		for (ScriptRoutineGraphProxy leftEvalProxy : left.getEvalProxies()) {
			target.appendEvalRoutine(leftEvalProxy.getTarget());
		}
		Log.log("After appending left evals, target has %d eval routines", target.getEvalProxyCount());
		for (List<ScriptRoutineGraph> routines : target.getEvalRoutineGroups()) {
			mergeEvalRoutines(routines);
		}
		target.compileEvalRoutines();

		for (ScriptRoutineGraph leftRoutine : left.getRoutines()) {
			ScriptRoutineGraph rightRoutine = target.getRoutine(leftRoutine.id);
			if (rightRoutine == null) {
				target.addRoutine(rightRoutine);
			} else {
				mergeRoutines(leftRoutine, rightRoutine);
			}
		}

		return target;
	}

	private void mergeRoutines(ScriptRoutineGraph leftRoutine, ScriptRoutineGraph targetRoutine) {
		int nodeCount = leftRoutine.getNodeCount();
		if (nodeCount != targetRoutine.getNodeCount())
			throw new MergeException("Node counts differ for routine 0x%x: %d vs. %d", leftRoutine.id, nodeCount,
					targetRoutine.getNodeCount());
		for (int i = 0; i < nodeCount; i++) {
			ScriptNode leftNode = leftRoutine.getNode(i);
			ScriptNode targetNode = targetRoutine.getNode(i);
			leftNode.verifyEqual(targetNode);

			switch (leftNode.type) {
				case CALL: {
					ScriptCallNode leftCall = (ScriptCallNode) leftNode;
					ScriptCallNode targetCall = (ScriptCallNode) targetNode;
					for (ScriptRoutineGraph leftTarget : leftCall.getTargets()) {
						if (targetCall.getTarget(leftTarget.id) == null) {
							ScriptRoutineGraph targetCallTarget = target.getRoutine(leftTarget.id);
							targetCall.addTarget(targetCallTarget);
						}
					}

				}
					break;
				case EVAL: {
					ScriptEvalNode leftEvalNode = (ScriptEvalNode) leftNode;
					ScriptEvalNode targetEvalNode = (ScriptEvalNode) targetNode;
					for (ScriptRoutineGraphProxy leftEval : leftEvalNode.getTargets()) {
						if (!targetEvalNode.hasTarget(leftEval.getEvalId())) {
							ScriptRoutineGraphProxy targetEval = target.getEvalProxy(leftEval.getEvalId());
							targetEvalNode.addTarget(targetEval);
						}
					}
				}
					break;
			}
		}
	}

	private void mergeEvalRoutines(List<ScriptRoutineGraph> routines) {
		int routineCount = routines.size();
		int opcode = routines.get(0).getNode(0).opcode;

		for (int i = 0; i < routines.size(); i++) {
			ScriptRoutineGraph keep = routines.get(i);
			for (int j = i + 1; j < routines.size();) {
				ScriptRoutineGraph compare = routines.get(j);
				if (keep.isSameRoutine(compare))
					routines.remove(j); // TODO: redirect all SRGProxies in the target to the kept instance
				else
					j++;
			}
		}
		Log.log("Merged %d eval routines starting with opcode %d into %d eval routines", routineCount, opcode,
				routines.size());
	}

	public static void main(String[] args) {

	}
}
