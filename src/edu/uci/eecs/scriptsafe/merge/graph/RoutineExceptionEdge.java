package edu.uci.eecs.scriptsafe.merge.graph;

public class RoutineExceptionEdge extends RoutineEdge {

	private int toRoutineIndex;

	public RoutineExceptionEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, int toRoutineIndex, int userLevel) {
		super(fromRoutineHash, fromIndex, toRoutineHash, userLevel);

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

	public String printToNode() {
		return String.format("0x%x|0x%x %d", toRoutineHash, toRoutineHash, toRoutineIndex);
	}
}
