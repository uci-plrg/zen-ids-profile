package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptFlowGraph {

	public final String description;
	private final Map<Long, ScriptRoutineGraph> routines = new HashMap<Long, ScriptRoutineGraph>();
	public final GraphEdgeSet graphEdgeSet = new GraphEdgeSet();
	private int maxDynamicRoutineId;

	public ScriptFlowGraph(String description) {
		this.description = description;
	}

	public void addRoutine(ScriptRoutineGraph routine) {
		if (routines.containsKey(routine.id))
			throw new MergeException("Attemp to add a routine 0x%x that already exists in the graph!", routine.id);

		if (ScriptRoutineGraph.isDynamicRoutine(routine.id))
			maxDynamicRoutineId = Math.max(maxDynamicRoutineId, ScriptRoutineGraph.getDynamicRoutineId(routine.id));

		routines.put(routine.id, routine);
	}

	public void appendDynamicRoutine(ScriptRoutineGraph dynamicRoutine) {
		ScriptRoutineGraph append = dynamicRoutine.rename(dynamicRoutine.unitHash, maxDynamicRoutineId++);
		routines.put(append.id, append);
	}

	public ScriptRoutineGraph getRoutine(Long id) {
		return routines.get(id);
	}

	public ScriptRoutineGraph getDynamicRoutine(int id) {
		return routines.get(ScriptRoutineGraph.constructDynamicId(id));
	}

	public int getRoutineCount() {
		return routines.size();
	}

	public Iterable<ScriptRoutineGraph> getRoutines() {
		return routines.values();
	}

	public int getMaxDynamicRoutineId() {
		return maxDynamicRoutineId;
	}

	public void checkIntegrity() {
	}
}
