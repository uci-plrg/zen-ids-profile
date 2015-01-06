package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ScriptCallNode extends ScriptNode {

	final Map<Long, ScriptRoutineGraph> staticTargets = new HashMap<Long, ScriptRoutineGraph>();
	private final List<ScriptRoutineGraphProxy> dynamicTargets = new ArrayList<ScriptRoutineGraphProxy>();

	public ScriptCallNode(int opcode, int index) {
		super(Type.CALL, opcode, index);
	}

	public void addStaticTarget(ScriptRoutineGraph target) {
		staticTargets.put(target.id, target);
	}

	public ScriptRoutineGraph getStaticTarget(Long id) {
		return staticTargets.get(id);
	}

	public Iterable<ScriptRoutineGraph> getStaticTargets() {
		return staticTargets.values();
	}

	public int getStaticTargetCount() {
		return staticTargets.size();
	}
	
	public boolean hasDynamicTarget(int id) {
		for (ScriptRoutineGraphProxy target : dynamicTargets) {
			if (target.getDynamicRoutineId() == id)
				return true;
		}
		return false;
	}

	public void addDynamicTarget(ScriptRoutineGraphProxy target) {
		dynamicTargets.add(target);
	}

	public Iterable<ScriptRoutineGraphProxy> getDynamicTargets() {
		return dynamicTargets;
	}

	public int getDynamicTargetCount() {
		return dynamicTargets.size();
	}

	public int getTargetCount() {
		return staticTargets.size() + dynamicTargets.size();
	}

	@Override
	public ScriptNode copy() {
		return new ScriptCallNode(opcode, index);
	}
}
