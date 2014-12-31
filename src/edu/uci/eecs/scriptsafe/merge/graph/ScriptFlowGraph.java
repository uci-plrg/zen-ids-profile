package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptFlowGraph {

	private final Map<Long, ScriptRoutineGraph> routines = new HashMap<Long, ScriptRoutineGraph>();
	private final List<ScriptRoutineGraphProxy> evalProxies = new ArrayList<ScriptRoutineGraphProxy>();

	public void addRoutine(ScriptRoutineGraph routine) {
		if (ScriptRoutineGraph.isEval(routine.id)) {
			if (ScriptRoutineGraph.getEvalId(routine.id) != (evalProxies.size() + 1))
				throw new MergeException("Expected eval id %d, but found id %d", evalProxies.size() + 1, routine.id);

			evalProxies.add(new ScriptRoutineGraphProxy(routine));
		} else {
			routines.put(routine.id, routine);
		}
	}

	public void appendEvalRoutine(ScriptRoutineGraph eval) {
		if (eval.unitHash != ScriptRoutineGraph.EVAL_UNIT_HASH)
			throw new MergeException("Attempt to append a non-eval routine 0x%x", eval.id);

		addRoutine(eval.rename(eval.unitHash, evalProxies.size() + 1));
	}

	public ScriptRoutineGraph getRoutine(Long id) {
		if (ScriptRoutineGraph.isEval(id)) {
			int evalId = ScriptRoutineGraph.getEvalId(id);
			if ((evalId - 1) < evalProxies.size())
				return evalProxies.get(evalId - 1).getTarget();
			else
				return null;
		} else {
			return routines.get(id);
		}
	}

	public int getRoutineCount() {
		return routines.size();
	}

	public Iterable<ScriptRoutineGraph> getRoutines() {
		return routines.values();
	}

	public ScriptRoutineGraphProxy getEvalProxy(int id) {
		return evalProxies.get(id - 1);
	}

	public int getEvalProxyCount() {
		return evalProxies.size();
	}

	public Iterable<ScriptRoutineGraphProxy> getEvalProxies() {
		return evalProxies;
	}

	protected void clearEvalProxies() {
		evalProxies.clear();
	}
}
