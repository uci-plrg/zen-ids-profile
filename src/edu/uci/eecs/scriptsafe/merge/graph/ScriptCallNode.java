package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

public class ScriptCallNode extends ScriptNode {

	private final Map<Long, ScriptRoutineGraph> targets = new HashMap<Long, ScriptRoutineGraph>();

	public ScriptCallNode(int opcode, int index) {
		super(opcode, index);
	}

	public void addTarget(ScriptRoutineGraph target) {
		targets.put(target.id, target);
	}

	public ScriptRoutineGraph getTarget(Long id) {
		return targets.get(id);
	}
}
