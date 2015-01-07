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
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraphProxy;

public class ScriptMerge {

	private final Map<ScriptCallNode, ScriptCallNode> callNodeMerges = new HashMap<ScriptCallNode, ScriptCallNode>();

	final ScriptFlowGraph left;
	final ScriptFlowGraph target;

	public ScriptMerge(ScriptFlowGraph left, ScriptFlowGraph right) {
		ScriptGraphCloner cloner = new ScriptGraphCloner();
		this.left = cloner.deepCopy(left);
		target = cloner.deepCopy(right);
	}

	public ScriptFlowGraph merge() {
		for (ScriptRoutineGraphProxy leftDynamicRoutineProxy : left.getDynamicRoutineProxies()) {
			target.appendDynamicRoutine(leftDynamicRoutineProxy);
		}
		Log.log("After appending left dynamic routines, target has %d dynamic routines",
				target.getDynamicRoutineProxyCount());
		mergeDynamicRoutines();

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
					for (ScriptRoutineGraph leftTarget : leftCall.getStaticTargets()) {
						if (targetCall.getStaticTarget(leftTarget.id) == null) {
							ScriptRoutineGraph targetCallTarget = target.getRoutine(leftTarget.id);
							Log.log("Merging new static routine entry 0x%x|0x%x I%d -> 0x%x|0x%x",
									leftRoutine.unitHash, leftRoutine.routineHash, i, targetCallTarget.unitHash,
									targetCallTarget.routineHash);
							targetCall.addStaticTarget(targetCallTarget);
						}
					}
					for (ScriptRoutineGraphProxy leftTarget : leftCall.getDynamicTargets()) {
						if (!targetCall.hasDynamicTarget(ScriptRoutineGraph.getDynamicRoutineId(leftTarget
								.getDynamicRoutineId()))) {
							Log.log("Merging new dynamic routine entry 0x%x|0x%x I%d -> 0x%x", leftRoutine.unitHash,
									leftRoutine.routineHash, i, leftTarget.getDynamicRoutineId());
							targetCall.addDynamicTarget(leftTarget);
						}
					}
				}
					break;
				case EVAL: {
					ScriptEvalNode leftEvalNode = (ScriptEvalNode) leftNode;
					ScriptEvalNode targetEvalNode = (ScriptEvalNode) targetNode;
					for (ScriptRoutineGraphProxy leftEval : leftEvalNode.getTargets()) {
						if (!targetEvalNode.hasTarget(leftEval.getDynamicRoutineId())) {
							Log.log("Merging new eval routine entry 0x%x|0x%x I%d -> 0x%x", leftRoutine.unitHash,
									leftRoutine.routineHash, i, leftEval.getDynamicRoutineId());
							targetEvalNode.addTarget(leftEval);
						}
					}
				}
					break;
			}
		}
	}

	private void mergeDynamicRoutines() {
		List<ScriptRoutineGraphProxy> dynamicRoutineProxies = target.getDynamicRoutineProxies();
		int evalProxyCount = dynamicRoutineProxies.size();

		for (int i = 0; i < dynamicRoutineProxies.size(); i++) {
			ScriptRoutineGraphProxy keep = dynamicRoutineProxies.get(i);
			if (keep.getDynamicRoutineId() != i) {
				ScriptRoutineGraph renamed = keep.getTarget().rename(ScriptRoutineGraph.DYNAMIC_UNIT_HASH, i);
				keep.setTarget(renamed);
			}
			for (int j = i + 1; j < dynamicRoutineProxies.size();) {
				ScriptRoutineGraphProxy compare = dynamicRoutineProxies.get(j);
				if (keep.getTarget().isSameRoutine(compare.getTarget())) {
					dynamicRoutineProxies.remove(j); //

					compare.setTarget(keep.getTarget());
				} else {
					j++;
				}
			}
		}
		Log.log("Merged %d dynamic routines into %d dynamic routines", evalProxyCount, dynamicRoutineProxies.size());
	}

	public static void main(String[] args) {

	}
}
