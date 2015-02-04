package edu.uci.eecs.scriptsafe.merge.graph;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptBranchNode extends ScriptNode {

	public static final int UNASSIGNED_BRANCH_TARGET_ID = 0xffffff;

	private int branchUserLevel;
	private ScriptNode target = null;

	public ScriptBranchNode(int opcode, int index, int branchUserLevel) {
		super(Type.BRANCH, opcode, index);

		this.branchUserLevel = branchUserLevel;
	}

	public void setTarget(ScriptNode target) {
		this.target = target;
	}

	public ScriptNode getTarget() {
		return target;
	}

	public int getBranchUserLevel() {
		return branchUserLevel;
	}

	public void setBranchUserLevel(int branchUserLevel) {
		this.branchUserLevel = branchUserLevel;
	}

	public int getTargetIndex(long routineId) {
		if (target == null) {
			switch (Opcode.forCode(opcode)) {
				case ZEND_BRK:
				case ZEND_CONT:
				case ZEND_CATCH:
					break;
				default:
					Log.error("Target missing for branch at %d with opcode 0x%x in 0x%x", index, opcode, routineId);
			}
			return UNASSIGNED_BRANCH_TARGET_ID;
		}
		return target.index;
	}

	@Override
	public void verifyEqual(ScriptNode other) {
		super.verifyEqual(other);

		switch (Opcode.forCode(opcode)) {
			case ZEND_BRK:
			case ZEND_CONT:
			case ZEND_CATCH:
				if (target == null || ((ScriptBranchNode) other).target == null)
					return;
		}

		if ((target == null) != (((ScriptBranchNode) other).target == null)) {
			Log.error("Target mismatch for branch node at index %d with opcode 0x%x: %s is null", index, opcode,
					target == null ? "this.target" : "other.target");
		}

		if (target != null) {
			if (target.index != ((ScriptBranchNode) other).target.index)
				throw new MergeException("Target mismatch for branch node at index %d", index);
		}
	}

	@Override
	public void verifyCompatible(ScriptNode other) {
		super.verifyEqual(other);

		switch (Opcode.forCode(opcode)) {
			case ZEND_BRK:
			case ZEND_CONT:
			case ZEND_CATCH:
				if (target == null || ((ScriptBranchNode) other).target == null)
					return;
		}

		if ((target == null) != (((ScriptBranchNode) other).target == null))
			return;

		if (target != null) {
			if (target.index != ((ScriptBranchNode) other).target.index)
				throw new MergeException("Target mismatch for branch node at index %d", index);
		}
	}

	@Override
	public ScriptNode copy() {
		return new ScriptBranchNode(opcode, index, branchUserLevel);
	}
}
