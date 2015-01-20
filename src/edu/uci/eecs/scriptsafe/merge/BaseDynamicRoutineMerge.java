package edu.uci.eecs.scriptsafe.merge;

import edu.uci.eecs.scriptsafe.merge.ScriptMerge.Side;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class BaseDynamicRoutineMerge extends DynamicRoutineMerge {

	private int rightRemapping[];

	public BaseDynamicRoutineMerge(ScriptFlowGraph left, ScriptFlowGraph right) {
		super(left);

		rightRemapping = new int[right.getMaxDynamicRoutineId() + 1];
	}

	@Override
	protected void remapRoutine(ScriptRoutineGraph routine, long toId, Side fromSide) {
		if (fromSide == ScriptMerge.Side.LEFT) {
			leftRemapping[ScriptRoutineGraph.getDynamicRoutineId(routine.id)] = ScriptRoutineGraph
					.getDynamicRoutineId(toId);
		} else {
			rightRemapping[ScriptRoutineGraph.getDynamicRoutineId(routine.id)] = ScriptRoutineGraph
					.getDynamicRoutineId(toId);
		}
	}

	@Override
	public int getNewRightDynamicRoutineId(int originalId) {
		return rightRemapping[originalId];
	}
}
