package edu.uci.eecs.scriptsafe.merge.graph;

public class RoutineEdge {

	public enum Type {
		CALL,
		THROW;
	}
	
	private long routineId;

	public RoutineEdge(long targetRoutineId) {
		this.routineId = targetRoutineId;
	}

	public boolean isSameEntryType(RoutineEdge other) {
		return other.getEntryType() == Type.CALL;
	}
	
	public Type getEntryType() {
		return Type.CALL;
	}
	
	public boolean hasDynamicTarget() {
		return ScriptRoutineGraph.isDynamicRoutine(routineId);
	}

	public int getDynamicRoutineId() {
		return ScriptRoutineGraph.getDynamicRoutineId(routineId);
	}
	
	public long getRoutineId() {
		return routineId;
	}

	public void setTargetRoutine(ScriptRoutineGraph routine) {
		this.routineId = routine.id;
	}
}
