package edu.uci.eecs.scriptsafe.merge.graph;

public class ScriptRoutineGraphProxy {

	private ScriptRoutineGraph target;

	public ScriptRoutineGraphProxy(ScriptRoutineGraph target) {
		this.target = target;
	}

	public int getEvalId() {
		return ScriptRoutineGraph.getEvalId(target.id);
	}

	public ScriptRoutineGraph getTarget() {
		return target;
	}

	public void setTarget(ScriptRoutineGraph target) {
		this.target = target;
	}
}
