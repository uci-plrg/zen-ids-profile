package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

public class ScriptFlowGraph {

	final Map<Long, ScriptRoutineGraph> routines = new HashMap<Long, ScriptRoutineGraph>();

	public void addRoutine(ScriptRoutineGraph routine) {
		routines.put(routine.id, routine);
	}

	public ScriptRoutineGraph getRoutine(Long id) {
		return routines.get(id);
	}

	public int getRoutineCount() {
		return routines.size();
	}

	public Iterable<ScriptRoutineGraph> getRoutines() {
		return routines.values();
	}
}
