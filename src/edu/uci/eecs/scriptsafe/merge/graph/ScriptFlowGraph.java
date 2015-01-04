package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;

public class ScriptFlowGraph {

	private final Map<Long, ScriptRoutineGraph> routines = new HashMap<Long, ScriptRoutineGraph>();
	private final List<ScriptRoutineGraphProxy> evalProxies = new ArrayList<ScriptRoutineGraphProxy>();

	public void addRoutine(ScriptRoutineGraph routine) {
		if (ScriptRoutineGraph.isEval(routine.id)) {
			if (ScriptRoutineGraph.getEvalId(routine.id) != evalProxies.size())
				throw new MergeException("Expected eval id %d, but found id %d", evalProxies.size(), routine.id);

			evalProxies.add(new ScriptRoutineGraphProxy(routine));
		} else {
			routines.put(routine.id, routine);
		}
	}

	public void appendEvalRoutine(ScriptRoutineGraphProxy evalProxy) {
		ScriptRoutineGraph append = evalProxy.getTarget()
				.rename(evalProxy.getTarget().unitHash, evalProxies.size());
		evalProxy.getTarget().setRedundant(true);
		evalProxy.setTarget(append);
		evalProxies.add(evalProxy);
	}

	public ScriptRoutineGraph getRoutine(Long id) {
		if (ScriptRoutineGraph.isEval(id)) {
			int evalId = ScriptRoutineGraph.getEvalId(id);
			if (evalId < evalProxies.size())
				return evalProxies.get(evalId).getTarget();
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
		return evalProxies.get(id);
	}

	public int getEvalProxyCount() {
		return evalProxies.size();
	}

	// dislike letting this out whole
	public List<ScriptRoutineGraphProxy> getEvalProxies() {
		return evalProxies;
	}

	protected void clearEvalProxies() {
		evalProxies.clear();
	}

	protected boolean hasEvalProxyFor(ScriptRoutineGraph eval) {
		for (ScriptRoutineGraphProxy evalProxy : evalProxies) {
			if (evalProxy.getTarget() == eval)
				return true;
		}
		return false;
	}

	public void redirectProxies(ScriptRoutineGraph discard, ScriptRoutineGraph keep) {
		for (ScriptRoutineGraphProxy evalProxy : evalProxies) {
			if (evalProxy.getTarget() == discard)
				evalProxy.setTarget(keep);
		}
		discard.setRedundant(true);
	}

	public void checkIntegrity() {
		int i = 0;
		for (ScriptRoutineGraphProxy target : getEvalProxies()) {
			if (target.getTarget().isRedundant())
				throw new MergeException("Redundant eval proxy in flow graph");
			if (target.getEvalId() != i) {
				throw new MergeException("Found eval proxy with an inconsistent id: expected %d but found %d", i,
						target.getEvalId());
			}
			i++;
		}
		for (ScriptRoutineGraph routine : getRoutines()) {
			for (ScriptNode node : routine.getNodes()) {
				if (node.type == Type.EVAL) {
					ScriptEvalNode eval = (ScriptEvalNode) node;
					for (ScriptRoutineGraphProxy target : eval.getTargets()) {
						if (!hasEvalProxyFor(target.getTarget()))
							throw new MergeException("Missing cached instance of eval proxy");
						if (target.getTarget().isRedundant())
							throw new MergeException("Redundant target of eval node");
					}
				}
			}
		}
	}
}
