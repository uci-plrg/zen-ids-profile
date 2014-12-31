package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

public class ScriptEvalNode extends ScriptNode {

	private final List<ScriptRoutineGraphProxy> targets = new ArrayList<ScriptRoutineGraphProxy>();

	public ScriptEvalNode(int opcode, int index) {
		super(Type.EVAL, opcode, index);
	}

	public void addTarget(ScriptRoutineGraphProxy target) {
		targets.add(target);
	}

	public ScriptRoutineGraphProxy getTarget(int id) {
		return targets.get(id);
	}

	public Iterable<ScriptRoutineGraphProxy> getTargets() {
		return targets;
	}

	public int getTargetCount() {
		return targets.size();
	}

	public boolean hasTarget(int id) {
		for (ScriptRoutineGraphProxy target : targets) {
			if (target.getEvalId() == id)
				return true;
		}
		return false;
	}
	
	@Override
	public ScriptNode copy() {
		return new ScriptEvalNode(opcode, index);
	}
}
