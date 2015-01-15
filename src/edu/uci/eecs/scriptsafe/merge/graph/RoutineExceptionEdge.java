package edu.uci.eecs.scriptsafe.merge.graph;

public class RoutineExceptionEdge extends RoutineEdge {

	private int targetIndex;

	public RoutineExceptionEdge(long targetRoutineId, int targetIndex) {
		super(targetRoutineId);

		this.targetIndex = targetIndex;
	}

	@Override
	public boolean isSameEntryType(RoutineEdge other) {
		return other.getEntryType() == RoutineEdge.Type.THROW
				&& targetIndex == ((RoutineExceptionEdge) other).getTargetIndex();
	}

	@Override
	public Type getEntryType() {
		return Type.THROW;
	}

	public int getTargetIndex() {
		return targetIndex;
	}

	public void setTargetIndex(int targetIndex) {
		this.targetIndex = targetIndex;
	}

}
