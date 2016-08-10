package edu.uci.plrg.cfi.php.merge;

import java.util.ArrayList;
import java.util.List;

import edu.uci.plrg.cfi.php.merge.graph.ScriptFlowGraph;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;

public abstract class DynamicRoutineMerge {

	final List<ScriptRoutineGraph> mergedGraphs = new ArrayList<ScriptRoutineGraph>();
	final int leftRemapping[];

	public DynamicRoutineMerge(ScriptFlowGraph left) {
		leftRemapping = new int[left.getMaxDynamicRoutineIndex() + 1];
	}

	abstract void remapRoutine(ScriptRoutineGraph routine, int toHash, DatasetMerge.Side fromSide);

	public void addDynamicRoutine(ScriptRoutineGraph routine, DatasetMerge.Side fromSide) {
		for (ScriptRoutineGraph merged : mergedGraphs) {
			if (routine.isSameRoutine(merged)) {
				remapRoutine(routine, merged.hash, fromSide);
				return;
			}
		}
		mergedGraphs.add(routine.renameDynamicRoutine(mergedGraphs.size(), routine.id, false));
	}

	public Iterable<ScriptRoutineGraph> getMergedGraphs() {
		return mergedGraphs;
	}

	public ScriptRoutineGraph getMergedGraph(int dynamicRoutineIndex, DatasetMerge.Side fromSide) {
		if (fromSide == DatasetMerge.Side.LEFT)
			return mergedGraphs.get(getNewLeftDynamicRoutineIndex(dynamicRoutineIndex));
		else
			return mergedGraphs.get(getNewRightDynamicRoutineIndex(dynamicRoutineIndex));
	}

	public int getNewLeftDynamicRoutineIndex(int originalIndex) {
		return leftRemapping[originalIndex];
	}

	public int getNewRightDynamicRoutineIndex(int originalIndex) {
		return originalIndex;
	}
}
