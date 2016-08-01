package edu.uci.plrg.cfi.php.merge;

import edu.uci.plrg.cfi.php.merge.DatasetMerge.Side;
import edu.uci.plrg.cfi.php.merge.graph.ScriptFlowGraph;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;

public class IncrementalDynamicRoutineMerge extends DynamicRoutineMerge {

	public IncrementalDynamicRoutineMerge(ScriptFlowGraph left) {
		super(left);
	}

	@Override
	protected void remapRoutine(ScriptRoutineGraph routine, int toHash, Side fromSide) {
		if (fromSide == Side.RIGHT)
			throw new MergeException("Attempt to append a dynamic routine from the right!");

		leftRemapping[ScriptRoutineGraph.getDynamicRoutineIndex(routine.hash)] = ScriptRoutineGraph
				.getDynamicRoutineIndex(toHash);
	}
}
