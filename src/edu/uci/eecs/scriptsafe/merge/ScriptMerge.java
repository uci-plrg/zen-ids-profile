package edu.uci.eecs.scriptsafe.merge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.GraphEdgeSet;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge.Type;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineExceptionEdge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptCallNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptEvalNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptMerge {

	public enum Side {
		LEFT,
		RIGHT;
	}

	final ScriptFlowGraph left;
	final ScriptFlowGraph right;

	private final Map<Long, ScriptRoutineGraph> mergedStaticRoutines = new HashMap<Long, ScriptRoutineGraph>();
	private final GraphEdgeSet graphEdgeSet = new GraphEdgeSet();
	private final DynamicRoutineMerge dynamicRoutineMerge;

	public ScriptMerge(ScriptFlowGraph left, ScriptFlowGraph right, boolean isIncremental) {
		this.left = left;
		this.right = right;

		if (isIncremental)
			dynamicRoutineMerge = new BaseDynamicRoutineMerge(left, right);
		else
			dynamicRoutineMerge = new IncrementalDynamicRoutineMerge(left);
	}

	public void merge() {
		for (ScriptRoutineGraph rightRoutine : right.getRoutines()) {
			if (ScriptRoutineGraph.isDynamicRoutine(rightRoutine.id))
				dynamicRoutineMerge.addDynamicRoutine(rightRoutine, Side.RIGHT);
			else
				mergedStaticRoutines.put(rightRoutine.id, rightRoutine);
		}
		for (ScriptRoutineGraph leftRoutine : left.getRoutines()) {
			if (ScriptRoutineGraph.isDynamicRoutine(leftRoutine.id)) {
				dynamicRoutineMerge.addDynamicRoutine(leftRoutine, Side.LEFT);
			} else {
				ScriptRoutineGraph rightRoutine = mergedStaticRoutines.get(leftRoutine.id);
				if (rightRoutine == null)
					mergedStaticRoutines.put(leftRoutine.id, leftRoutine);
				else
					leftRoutine.verifySameRoutine(rightRoutine);
			}
		}

		// patch the dynamic edges
		// merge the routine edges

		for (List<RoutineEdge> leftEdges : left.graphEdgeSet.getOutgoingEdges()) {
			for (RoutineEdge leftEdge : leftEdges) {
				if (leftEdge.getEntryType() == Type.THROW) {
					RoutineExceptionEdge throwEdge = (RoutineExceptionEdge) leftEdge;
					leftEdge.setToRoutineId(dynamicRoutineMerge.getNewLeftDynamicRoutineId(ScriptRoutineGraph
							.getDynamicRoutineId(throwEdge.getToRoutineId())));
					graphEdgeSet.addExceptionEdge(
							getNode(throwEdge.getFromRoutineId(), throwEdge.getFromRoutineIndex()),
							dynamicRoutineMerge.getMergedGraph(ScriptRoutineGraph.getDynamicRoutineId(throwEdge
									.getToRoutineId())), throwEdge.getToRoutineIndex());
				} else {
					graphEdgeSet.addCallEdge(getNode(leftEdge.getFromRoutineId(), leftEdge.getFromRoutineIndex()),
							mergedStaticRoutines.get(leftEdge.getToRoutineId()));
				}
			}
		}

		// dataset generator writes from here (interface so it can also write plain graphs?)
		// nix cloner (hopefully)
	}

	private ScriptNode getNode(long routineId, int index) {
		if (ScriptRoutineGraph.isDynamicRoutine(routineId))
			return dynamicRoutineMerge.getMergedGraph(ScriptRoutineGraph.getDynamicRoutineId(routineId)).getNode(index);
		else
			return mergedStaticRoutines.get(routineId).getNode(index);
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
							ScriptRoutineGraph targetCallTarget = right.getRoutine(leftTarget.id);
							Log.log("Merging new static routine entry 0x%x|0x%x I%d -> 0x%x|0x%x",
									leftRoutine.unitHash, leftRoutine.routineHash, i, targetCallTarget.unitHash,
									targetCallTarget.routineHash);
							targetCall.addStaticTarget(targetCallTarget);
						}
					}
					for (RoutineEdge leftTarget : leftCall.getDynamicTargets()) {
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
					for (RoutineEdge leftEval : leftEvalNode.getTargets()) {
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
}
