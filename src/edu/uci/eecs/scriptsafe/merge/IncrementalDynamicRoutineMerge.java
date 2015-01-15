package edu.uci.eecs.scriptsafe.merge;

import edu.uci.eecs.scriptsafe.merge.ScriptMerge.Side;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class IncrementalDynamicRoutineMerge extends DynamicRoutineMerge {

	public IncrementalDynamicRoutineMerge(ScriptFlowGraph left) {
		super(left);
	}

	@Override
	protected void remapRoutine(ScriptRoutineGraph routine, long toId, Side fromSide) {
		if (fromSide == Side.RIGHT)
			throw new MergeException("Dynamic routines should only be appended to the merge from the left!");

		leftRemapping[ScriptRoutineGraph.getDynamicRoutineId(routine.id)] = ScriptRoutineGraph
				.getDynamicRoutineId(toId);
	}
}
