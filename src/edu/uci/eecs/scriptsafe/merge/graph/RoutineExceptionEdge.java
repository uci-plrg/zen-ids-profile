package edu.uci.eecs.scriptsafe.merge.graph;

public class RoutineExceptionEdge extends RoutineEdge {

	private int toRoutineIndex;

	public RoutineExceptionEdge(long fromRoutineId, int fromIndex, long toRoutineId, int toRoutineIndex, int userLevel) {
		super(fromRoutineId, fromIndex, toRoutineId, userLevel);

		this.toRoutineIndex = toRoutineIndex;
	}

	@Override
	public boolean isSameEntryType(RoutineEdge other) {
		return other.getEntryType() == RoutineEdge.Type.THROW
				&& toRoutineIndex == ((RoutineExceptionEdge) other).getToRoutineIndex();
	}

	@Override
	public Type getEntryType() {
		return Type.THROW;
	}

	public int getToRoutineIndex() {
		return toRoutineIndex;
	}

	public void setToRoutineIndex(int toRoutineIndex) {
		this.toRoutineIndex = toRoutineIndex;
	}

}
