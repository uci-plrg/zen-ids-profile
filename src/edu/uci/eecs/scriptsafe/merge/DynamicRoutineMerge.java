package edu.uci.eecs.scriptsafe.merge;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public abstract class DynamicRoutineMerge {

	final List<ScriptRoutineGraph> mergedGraphs = new ArrayList<ScriptRoutineGraph>();
	final int leftRemapping[];

	public DynamicRoutineMerge(ScriptFlowGraph left) {
		leftRemapping = new int[left.getMaxDynamicRoutineId()];
	}

	abstract void remapRoutine(ScriptRoutineGraph routine, long toId, ScriptMerge.Side fromSide);

	public void addDynamicRoutine(ScriptRoutineGraph routine, ScriptMerge.Side fromSide) {
		for (ScriptRoutineGraph merged : mergedGraphs) {
			if (routine.isSameRoutine(merged)) {
				remapRoutine(routine, merged.id, fromSide);
			} else {
				mergedGraphs.add(routine.rename(ScriptRoutineGraph.DYNAMIC_UNIT_HASH, mergedGraphs.size()));
			}
		}
	}

	public Iterable<ScriptRoutineGraph> getMergedGraphs() {
		return mergedGraphs;
	}

	public ScriptRoutineGraph getMergedGraph(int dynamicRoutineId, ScriptMerge.Side fromSide) {
		if (fromSide == ScriptMerge.Side.LEFT)
			return mergedGraphs.get(getNewLeftDynamicRoutineId(dynamicRoutineId));
		else
			return mergedGraphs.get(getNewRightDynamicRoutineId(dynamicRoutineId));
	}

	public int getNewLeftDynamicRoutineId(int originalId) {
		return leftRemapping[originalId];
	}

	public int getNewRightDynamicRoutineId(int originalId) {
		return originalId;
	}
}
