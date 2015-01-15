package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

public class ScriptEvalNode extends ScriptNode {

	private final List<RoutineEdge> targets = new ArrayList<RoutineEdge>();

	public ScriptEvalNode(int opcode, int index) {
		super(Type.EVAL, opcode, index);
	}

	public void addTarget(RoutineEdge target) {
		targets.add(target);
	}

	public Iterable<RoutineEdge> getTargets() {
		return targets;
	}

	public int getTargetCount() {
		return targets.size();
	}

	public boolean hasTarget(int id) {
		for (RoutineEdge target : targets) {
			if (target.getDynamicRoutineId() == id)
				return true;
		}
		return false;
	}
	
	@Override
	public ScriptNode copy() {
		return new ScriptEvalNode(opcode, index);
	}
}
