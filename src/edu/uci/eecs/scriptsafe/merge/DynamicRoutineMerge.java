package edu.uci.eecs.scriptsafe.merge;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public abstract class DynamicRoutineMerge {

	protected final List<ScriptRoutineGraph> mergedGraphs = new ArrayList<ScriptRoutineGraph>();
	protected final int leftRemapping[];

	public DynamicRoutineMerge(ScriptFlowGraph left) {
		leftRemapping = new int[left.getMaxDynamicRoutineId()];
	}

	protected abstract void remapRoutine(ScriptRoutineGraph routine, long toId, ScriptMerge.Side fromSide);

	public void addDynamicRoutine(ScriptRoutineGraph routine, ScriptMerge.Side fromSide) {
		for (ScriptRoutineGraph merged : mergedGraphs) {
			if (routine.isSameRoutine(merged)) {
				remapRoutine(routine, merged.id, fromSide);
			} else {
				mergedGraphs.add(routine.rename(ScriptRoutineGraph.DYNAMIC_UNIT_HASH, mergedGraphs.size()));
			}
		}
	}

	public List<ScriptRoutineGraph> getMergedGraphs() {
		return mergedGraphs;
	}

	public int getNewLeftDynamicRoutineId(int originalId) {
		return leftRemapping[originalId];
	}

	public int getNewRightDynamicRoutineId(int originalId) {
		return originalId;
	}
}
