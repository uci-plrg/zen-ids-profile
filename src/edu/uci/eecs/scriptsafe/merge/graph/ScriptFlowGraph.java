package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptFlowGraph {

	public final String description;
	public final boolean isFragmentary;
	private final Map<Integer, ScriptRoutineGraph> routines = new HashMap<Integer, ScriptRoutineGraph>();
	public final GraphEdgeSet edges = new GraphEdgeSet();
	private int maxDynamicRoutineIndex;

	public ScriptFlowGraph(String description, boolean isFragmentary) {
		this.description = description;
		this.isFragmentary = isFragmentary;
	}

	public void addRoutine(ScriptRoutineGraph routine) {
		if (routines.containsKey(routine.hash))
			throw new MergeException("Attemp to add a routine 0x%x that already exists in the graph!", routine.hash);

		if (ScriptRoutineGraph.isDynamicRoutine(routine.hash))
			maxDynamicRoutineIndex = Math.max(maxDynamicRoutineIndex,
					ScriptRoutineGraph.getDynamicRoutineIndex(routine.hash));

		routines.put(routine.hash, routine);
	}

	public void appendDynamicRoutine(ScriptRoutineGraph dynamicRoutine) {
		ScriptRoutineGraph append = dynamicRoutine.renameDynamicRoutine(maxDynamicRoutineIndex++, false);
		routines.put(append.hash, append);
	}

	public ScriptRoutineGraph getRoutine(Integer hash) {
		return routines.get(hash);
	}

	public ScriptRoutineGraph getDynamicRoutine(int index) {
		return routines.get(ScriptRoutineGraph.constructDynamicHash(index));
	}

	public int getRoutineCount() {
		return routines.size();
	}

	public Iterable<ScriptRoutineGraph> getRoutines() {
		return routines.values();
	}

	public int getMaxDynamicRoutineIndex() {
		return maxDynamicRoutineIndex;
	}

	public void checkIntegrity() {
	}
}
