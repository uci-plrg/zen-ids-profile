package edu.uci.plrg.cfi.php.merge;

import edu.uci.plrg.cfi.php.merge.DatasetMerge.Side;
import edu.uci.plrg.cfi.php.merge.graph.ScriptFlowGraph;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;

public class BaseDynamicRoutineMerge extends DynamicRoutineMerge {

	private int rightRemapping[];

	public BaseDynamicRoutineMerge(ScriptFlowGraph left, ScriptFlowGraph right) {
		super(left);

		rightRemapping = new int[right.getMaxDynamicRoutineIndex() + 1];
	}

	@Override
	protected void remapRoutine(ScriptRoutineGraph routine, int toHash, Side fromSide) {
		if (fromSide == DatasetMerge.Side.LEFT) {
			leftRemapping[ScriptRoutineGraph.getDynamicRoutineIndex(routine.hash)] = ScriptRoutineGraph
					.getDynamicRoutineIndex(toHash);
		} else {
			rightRemapping[ScriptRoutineGraph.getDynamicRoutineIndex(routine.hash)] = ScriptRoutineGraph
					.getDynamicRoutineIndex(toHash);
		}
	}

	@Override
	public int getNewRightDynamicRoutineIndex(int originalIndex) {
		return rightRemapping[originalIndex];
	}
}
