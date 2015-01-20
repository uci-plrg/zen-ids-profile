package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;

public class ScriptGraphCloner {

	private final Map<ScriptNode, ScriptNode> nodeCopies = new HashMap<ScriptNode, ScriptNode>();
	private ScriptFlowGraph flowCopy;

	public ScriptFlowGraph copyRoutines(ScriptFlowGraph original, ScriptFlowGraph flowCopy) {
		ScriptNode fromNode;

		for (ScriptRoutineGraph routine : original.getRoutines()) {
			ScriptRoutineGraph routineCopy = routine.copy();
			shallowCopy(routine, routineCopy);
			flowCopy.addRoutine(routineCopy);
		}

		Log.log("Copying %d dynamic routines", original.getMaxDynamicRoutineId());
		for (List<RoutineEdge> edges : original.edges.getOutgoingEdges()) {
			for (RoutineEdge edge : edges) {
				fromNode = flowCopy.getRoutine(edge.getFromRoutineId()).getNode(edge.getFromRoutineIndex());
				switch (edge.getEntryType()) {
					case CALL:
						flowCopy.edges.addCallEdge(edge.getFromRoutineId(), fromNode, edge.getToRoutineId());
						break;
					case THROW:
						flowCopy.edges.addExceptionEdge(edge.getFromRoutineId(), fromNode, edge.getToRoutineId(),
								((RoutineExceptionEdge) edge).getToRoutineIndex());
						break;
				}
			}
		}
		Log.log("Copy now has %d dynamic routines", flowCopy.getMaxDynamicRoutineId());

		return flowCopy;
	}

	private void shallowCopy(ScriptRoutineGraph routineOriginal, ScriptRoutineGraph routineCopy) {
		for (int i = 0; i < routineOriginal.getNodeCount(); i++) {
			ScriptNode nodeOriginal = routineOriginal.getNode(i);
			ScriptNode nodeCopy = nodeOriginal.copy();
			routineCopy.addNode(nodeCopy);
		}
	}
}
