package edu.uci.eecs.scriptsafe.merge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptCallNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptEvalNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptGraphCloner;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraphProxy;

public class ScriptMerge {

	private final Map<ScriptCallNode, ScriptCallNode> callNodeMerges = new HashMap<ScriptCallNode, ScriptCallNode>();

	final ScriptFlowGraph left;
	final ScriptFlowGraph target;

	private int evalId = 0;

	public ScriptMerge(ScriptFlowGraph left, ScriptFlowGraph right) {
		ScriptGraphCloner cloner = new ScriptGraphCloner();
		this.left = cloner.deepCopy(left);
		target = cloner.deepCopy(right);
	}

	public ScriptFlowGraph merge() {
		for (ScriptRoutineGraphProxy leftEvalProxy : left.getEvalProxies()) {
			target.appendEvalRoutine(leftEvalProxy);
		}
		Log.log("After appending left evals, target has %d eval routines", target.getEvalProxyCount());
		mergeEvalRoutines();

		for (ScriptRoutineGraph leftRoutine : left.getRoutines()) {
			ScriptRoutineGraph rightRoutine = target.getRoutine(leftRoutine.id);
			if (rightRoutine == null) {
				target.addRoutine(leftRoutine);
			} else {
				mergeRoutines(leftRoutine, rightRoutine);
			}
		}

		target.checkIntegrity();

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
						if (!targetEvalNode.hasTarget(leftEval.getEvalId()))
							targetEvalNode.addTarget(leftEval);
					}
				}
					break;
			}
		}
	}

	private void mergeEvalRoutines() {
		List<ScriptRoutineGraphProxy> evalProxies = target.getEvalProxies();
		int evalProxyCount = evalProxies.size();

		for (int i = 0; i < evalProxies.size(); i++) {
			ScriptRoutineGraphProxy keep = evalProxies.get(i);
			if (keep.getEvalId() != i) {
				ScriptRoutineGraph renamed = keep.getTarget().rename(ScriptRoutineGraph.EVAL_UNIT_HASH, i);
				keep.setTarget(renamed);
			}
			for (int j = i + 1; j < evalProxies.size();) {
				ScriptRoutineGraphProxy compare = evalProxies.get(j);
				if (keep.getTarget().isSameRoutine(compare.getTarget())) {
					evalProxies.remove(j); //

					compare.setTarget(keep.getTarget());
				} else {
					j++;
				}
			}
		}
		Log.log("Merged %d eval routines into %d eval routines", evalProxyCount, evalProxies.size());
	}

	public static void main(String[] args) {

	}
}
