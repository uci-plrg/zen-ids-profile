package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptMergeTarget extends ScriptFlowGraph {

	final Map<Integer, List<ScriptRoutineGraph>> evalRoutinesByFirstOpcode = new HashMap<Integer, List<ScriptRoutineGraph>>();

	public void addEvalRoutine(ScriptRoutineGraph eval) {
		if (eval.unitHash != ScriptRoutineGraph.EVAL_UNIT_HASH)
			throw new MergeException("Routine 0x%x is not an eval!", eval.id);

		Log.log("Pooling eval with id 0x%x", eval.id);

		getEvalRoutines(eval.getNode(0).opcode).add(eval);
	}

	public Iterable<List<ScriptRoutineGraph>> getEvalRoutineGroups() {
		return evalRoutinesByFirstOpcode.values();
	}

	public void compileEvalRoutines() {
		int evalId = 1;
		clearEvalProxies();
		for (List<ScriptRoutineGraph> routines : evalRoutinesByFirstOpcode.values()) {
			for (ScriptRoutineGraph routine : routines)
				super.addRoutine(routine.rename(ScriptRoutineGraph.EVAL_UNIT_HASH, evalId++));
		}
	}

	@Override
	public void addRoutine(ScriptRoutineGraph routine) {
		if (routine.unitHash == ScriptRoutineGraph.EVAL_UNIT_HASH)
			addEvalRoutine(routine);

		super.addRoutine(routine);
	}
	
	private List<ScriptRoutineGraph> getEvalRoutines(int opcode) {
		List<ScriptRoutineGraph> routines = evalRoutinesByFirstOpcode.get(opcode);
		if (routines == null) {
			routines = new ArrayList<ScriptRoutineGraph>();
			evalRoutinesByFirstOpcode.put(opcode, routines);
		}
		return routines;
	}
}
