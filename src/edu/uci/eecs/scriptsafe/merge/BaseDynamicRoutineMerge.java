package edu.uci.eecs.scriptsafe.merge;

import edu.uci.eecs.scriptsafe.merge.ScriptMerge.Side;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class BaseDynamicRoutineMerge extends DynamicRoutineMerge {

	private int rightRemapping[];

	public BaseDynamicRoutineMerge(ScriptFlowGraph left, ScriptFlowGraph right) {
		super(left);

		rightRemapping = new int[right.getMaxDynamicRoutineIndex() + 1];
	}

	@Override
	protected void remapRoutine(ScriptRoutineGraph routine, int toHash, Side fromSide) {
		if (fromSide == ScriptMerge.Side.LEFT) {
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
