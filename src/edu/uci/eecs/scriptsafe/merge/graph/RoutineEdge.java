package edu.uci.eecs.scriptsafe.merge.graph;

public class RoutineEdge {

	public enum Type {
		CALL,
		THROW;
	}

	private long fromRoutineId;
	private int fromIndex;
	private long toRoutineId;
	private int userLevel;

	public RoutineEdge(long fromRoutineId, int fromIndex, long toRoutineId, int userLevel) {
		this.fromRoutineId = fromRoutineId;
		this.fromIndex = fromIndex;
		this.toRoutineId = toRoutineId;
		this.userLevel = userLevel;
	}

	public boolean isSameEntryType(RoutineEdge other) {
		return other.getEntryType() == Type.CALL;
	}

	public Type getEntryType() {
		return Type.CALL;
	}

	public long getFromRoutineId() {
		return fromRoutineId;
	}

	public int getFromRoutineIndex() {
		return fromIndex;
	}

	public long getToRoutineId() {
		return toRoutineId;
	}

	public void setToRoutineId(long toRoutineId) {
		this.toRoutineId = toRoutineId;
	}

	public int getUserLevel() {
		return userLevel;
	}

	public void setUserLevel(int userLevel) {
		this.userLevel = userLevel;
	}
}
