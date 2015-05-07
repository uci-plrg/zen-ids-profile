package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.scriptsafe.merge.MergeException;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles;

public class ScriptFlowGraph {

	private static ScriptRoutineGraph ENTRY_ROUTINE = new ScriptRoutineGraph(1, RoutineId.ENTRY_ID, false);

	public final ScriptGraphDataFiles.Type dataSourceType;
	public final String description;
	public final boolean isFragmentary;
	private final Map<Integer, ScriptRoutineGraph> routines = new HashMap<Integer, ScriptRoutineGraph>();
	public final GraphEdgeSet edges = new GraphEdgeSet();
	private int maxDynamicRoutineIndex;

	public ScriptFlowGraph(ScriptGraphDataFiles.Type dataSourceType, String description, boolean isFragmentary) {
		this.dataSourceType = dataSourceType;
		this.description = description;
		this.isFragmentary = isFragmentary;

		// routines.put(1, ENTRY_ROUTINE);
	}

	public void addRoutine(ScriptRoutineGraph routine) {
		if (routines.containsKey(routine.hash))
			throw new MergeException("Attempt to add a routine 0x%x that already exists in the graph!", routine.hash);

		if (ScriptRoutineGraph.isDynamicRoutine(routine.hash))
			maxDynamicRoutineIndex = Math.max(maxDynamicRoutineIndex,
					ScriptRoutineGraph.getDynamicRoutineIndex(routine.hash));

		routines.put(routine.hash, routine);
	}

	public void appendDynamicRoutine(ScriptRoutineGraph dynamicRoutine) {
		ScriptRoutineGraph append = dynamicRoutine.renameDynamicRoutine(maxDynamicRoutineIndex++, dynamicRoutine.id,
				false);
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
