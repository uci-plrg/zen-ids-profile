package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.scriptsafe.merge.MergeException;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;

public class ScriptFlowGraph {

	public final String description;
	private final Map<Long, ScriptRoutineGraph> routines = new HashMap<Long, ScriptRoutineGraph>();
	private final List<ScriptRoutineGraphProxy> dynamicRoutineProxies = new ArrayList<ScriptRoutineGraphProxy>();

	public ScriptFlowGraph(String description) {
		this.description = description;
	}

	public void addRoutine(ScriptRoutineGraph routine) {
		if (ScriptRoutineGraph.isDynamicRoutine(routine.id)) {
			// if (ScriptRoutineGraph.getDynamicRoutineId(routine.id) != dynamicRoutineProxies.size())
			// throw new MergeException("Expected dynamic routine id %d, but found id %d", dynamicRoutineProxies.size(),
			// routine.id);

			while (ScriptRoutineGraph.getDynamicRoutineId(routine.id) >= dynamicRoutineProxies.size())
				dynamicRoutineProxies.add(null);

			dynamicRoutineProxies.set(ScriptRoutineGraph.getDynamicRoutineId(routine.id), new ScriptRoutineGraphProxy(
					routine));
		} else {
			routines.put(routine.id, routine);
		}
	}

	public void appendDynamicRoutine(ScriptRoutineGraphProxy dynamicRoutineProxy) {
		ScriptRoutineGraph append = dynamicRoutineProxy.getTarget().rename(dynamicRoutineProxy.getTarget().unitHash,
				dynamicRoutineProxies.size());
		dynamicRoutineProxy.getTarget().setRedundant(true);
		dynamicRoutineProxy.setTarget(append);
		dynamicRoutineProxies.add(dynamicRoutineProxy);
	}

	public ScriptRoutineGraph getRoutine(Long id) {
		if (ScriptRoutineGraph.isDynamicRoutine(id)) {
			int dynamicRoutineId = ScriptRoutineGraph.getDynamicRoutineId(id);
			if (dynamicRoutineId < dynamicRoutineProxies.size())
				return dynamicRoutineProxies.get(dynamicRoutineId).getTarget();
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

	public ScriptRoutineGraphProxy getDynamicRoutineProxy(int id) {
		return dynamicRoutineProxies.get(id);
	}

	public int getDynamicRoutineProxyCount() {
		return dynamicRoutineProxies.size();
	}

	// dislike letting this out whole
	public List<ScriptRoutineGraphProxy> getDynamicRoutineProxies() {
		return dynamicRoutineProxies;
	}

	protected void clearDynamicRoutineProxies() {
		dynamicRoutineProxies.clear();
	}

	protected boolean hasDynamicRoutineProxyFor(ScriptRoutineGraph eval) {
		for (ScriptRoutineGraphProxy dynamicRoutineProxy : dynamicRoutineProxies) {
			if (dynamicRoutineProxy.getTarget() == eval)
				return true;
		}
		return false;
	}

	public void redirectProxies(ScriptRoutineGraph discard, ScriptRoutineGraph keep) {
		for (ScriptRoutineGraphProxy dynamicRoutineProxy : dynamicRoutineProxies) {
			if (dynamicRoutineProxy.getTarget() == discard)
				dynamicRoutineProxy.setTarget(keep);
		}
		discard.setRedundant(true);
	}

	public void checkIntegrity() {
		int i = 0;
		for (ScriptRoutineGraphProxy target : getDynamicRoutineProxies()) {
			if (target.getTarget().isRedundant())
				throw new MergeException("Redundant dynamic routine proxy in flow graph");
			if (target.getDynamicRoutineId() != i) {
				throw new MergeException(
						"Found dynamic routine proxy with an inconsistent id: expected %d but found %d", i,
						target.getDynamicRoutineId());
			}
			i++;
		}
		for (ScriptRoutineGraph routine : getRoutines()) {
			for (ScriptNode node : routine.getNodes()) {
				if (node.type == Type.EVAL) {
					ScriptEvalNode eval = (ScriptEvalNode) node;
					for (ScriptRoutineGraphProxy target : eval.getTargets()) {
						if (!hasDynamicRoutineProxyFor(target.getTarget()))
							throw new MergeException("Missing cached instance of dynamic routine proxy");
						if (target.getTarget().isRedundant())
							throw new MergeException("Redundant target of dynamic routine node");
					}
				}
			}
		}
	}
}
