package edu.uci.eecs.scriptsafe.merge;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptCallNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptGraphCloner;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptMerge {

	private final Map<ScriptCallNode, ScriptCallNode> callNodeMerges = new HashMap<ScriptCallNode, ScriptCallNode>();

	final ScriptFlowGraph left;
	final ScriptFlowGraph target;

	public ScriptMerge(ScriptFlowGraph left, ScriptFlowGraph right) {
		this.left = left;

		ScriptGraphCloner cloner = new ScriptGraphCloner();
		target = cloner.deepCopy(right);
	}

	public ScriptFlowGraph merge() {
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

			if (leftNode.type == ScriptNode.Type.CALL) {
				ScriptCallNode leftCall = (ScriptCallNode) leftNode;
				ScriptCallNode targetCall = (ScriptCallNode) targetNode;
				for (ScriptRoutineGraph leftTarget : leftCall.getTargets()) {
					if (targetCall.getTarget(leftTarget.id) == null) {
						ScriptRoutineGraph targetCallTarget = target.getRoutine(leftTarget.id);
						targetCall.addTarget(targetCallTarget);
					}
				}
			}
		}
	}

	public static void main(String[] args) {

	}
}
