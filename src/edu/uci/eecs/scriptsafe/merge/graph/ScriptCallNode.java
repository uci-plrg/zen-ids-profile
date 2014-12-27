package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

public class ScriptCallNode extends ScriptNode {

	final Map<Long, ScriptRoutineGraph> targets = new HashMap<Long, ScriptRoutineGraph>();

	public ScriptCallNode(int opcode, int index) {
		super(Type.CALL, opcode, index);
	}

	public void addTarget(ScriptRoutineGraph target) {
		targets.put(target.id, target);
	}

	public ScriptRoutineGraph getTarget(Long id) {
		return targets.get(id);
	}
	
	public Iterable<ScriptRoutineGraph> getTargets() {
		return targets.values();
	}
}
