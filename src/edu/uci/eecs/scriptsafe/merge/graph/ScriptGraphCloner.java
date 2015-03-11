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
			ScriptRoutineGraph routineCopy = routine.copy(true);
			shallowCopy(routine, routineCopy);
			flowCopy.addRoutine(routineCopy);
		}

		Log.message("Copying %d dynamic routines", original.getMaxDynamicRoutineIndex());
		for (List<RoutineEdge> edges : original.edges.getOutgoingEdges()) {
			for (RoutineEdge edge : edges) {
				fromNode = flowCopy.getRoutine(edge.getFromRoutineHash()).getNode(edge.getFromRoutineIndex());
				switch (edge.getEntryType()) {
					case CALL:
						flowCopy.edges.addCallEdge(edge.getFromRoutineHash(), fromNode, edge.getToRoutineHash(),
								edge.getUserLevel());
						break;
					case THROW:
						flowCopy.edges.addExceptionEdge(edge.getFromRoutineHash(), fromNode, edge.getToRoutineHash(),
								((RoutineExceptionEdge) edge).getToRoutineIndex(), edge.getUserLevel());
						break;
				}
			}
		}
		Log.message("Copy now has %d dynamic routines", flowCopy.getMaxDynamicRoutineIndex());

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
