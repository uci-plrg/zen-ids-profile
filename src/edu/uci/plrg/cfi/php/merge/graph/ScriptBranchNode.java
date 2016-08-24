package edu.uci.plrg.cfi.php.merge.graph;

import java.util.Set;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.php.merge.MergeException;

public class ScriptBranchNode extends ScriptNode {

	public static final int UNASSIGNED_BRANCH_TARGET_ID = 0xffffff;

	private int branchUserLevel;
	private ScriptNode target = null;

	public ScriptBranchNode(int routineHash, Set<TypeFlag> typeFlags, int opcode, int index, int lineNumber,
			int branchUserLevel) {
		super(routineHash, typeFlags, opcode, lineNumber, index);

		this.branchUserLevel = branchUserLevel;
	}

	public void setTarget(ScriptNode target) {
		if (target == null && ScriptNode.Opcode.forCode(opcode).targetType == OpcodeTargetType.REQUIRED)
			throw new MergeException("Cannot set a null target on a branch with opcode 0x%x", opcode);
		// if (target != null && isFallThrough(target.index))
		// throw new MergeException("The branch fall-through is implied and should not be set on the node.");

		this.target = target;
	}

	public ScriptNode getTarget() {
		return target;
	}

	public boolean isFallThrough(int targetIndex) {
		return (isConditional() && getNext().index == targetIndex);
	}

	public int getBranchUserLevel() {
		return branchUserLevel;
	}

	public void setBranchUserLevel(int branchUserLevel) {
		this.branchUserLevel = branchUserLevel;
	}

	public int getTargetIndex() {
		if (target == null)
			return UNASSIGNED_BRANCH_TARGET_ID;
		else
			return target.index;
	}

	public boolean isConditional() {
		switch (Opcode.forCode(opcode)) {
			case ZEND_JMP:
			case ZEND_FE_FETCH_R:
				return false;
			case ZEND_FE_RESET_R:
			case ZEND_JMPZ:
			case ZEND_JMPNZ:
			case ZEND_JMPNZ_EX:
			case ZEND_JMPZ_EX:
			case ZEND_JMPZNZ:
			case ZEND_CATCH:
			default:
				return true;
		}
	}

	@Override
	public void verifyEqual(ScriptNode node) {
		super.verifyEqual(node);

		ScriptBranchNode other = (ScriptBranchNode) node;

		switch (Opcode.forCode(opcode)) {
			case ZEND_CATCH:
			case ZEND_JMPZNZ: /* non-sequential branch! Rarely matters in WordPress... */
				return;
			default:
				;
		}
		if (target == null || other.target == null)
			return;

		if ((target == null) != (other.target == null)) {
			Log.error("Target mismatch for branch node at index %d with opcode 0x%x: %s is null", index, opcode,
					target == null ? "this.target" : "other.target");
		}

		if (target != null) {
			if (target.index != other.target.index
					&& !(isFallThrough(target.index) || isFallThrough(other.target.index))) {
				ScriptBranchNode otherNode = (ScriptBranchNode) other;
				boolean patched = false;

				if (!isConditional()) {
					if (target.index == index + 1) {
						patched = true;
						target = otherNode.target;
					} else if (otherNode.target.index == index + 1) {
						patched = true;
						otherNode.target = target;
					}

					if (patched)
						Log.error("Patching impossible fall-through from unconditional branch 0x%x\n", opcode);
				}

				if (!patched) {
					throw new MergeException(
							"Target mismatch for branch node at index %d of 0x%x with opcode 0x%x: %d vs. %d", index,
							routineHash, opcode, target.index, ((ScriptBranchNode) other).target.index);
				}
			}
		}
	}

	@Override
	public void verifyCompatible(ScriptNode node) {
		super.verifyEqual(node);

		ScriptBranchNode other = (ScriptBranchNode) node;

		if (Opcode.forCode(opcode) == Opcode.ZEND_CATCH && (target == null || other.target == null))
			return;

		if ((target == null) != (other.target == null))
			return;

		if (target != null) {
			if (target.index != other.target.index
					&& !(isFallThrough(target.index) || isFallThrough(other.target.index))) {
				ScriptBranchNode otherNode = (ScriptBranchNode) other;
				boolean patched = false;

				if (!isConditional()) {
					if (target.index == index + 1) {
						patched = true;
						target = otherNode.target;
					} else if (otherNode.target.index == index + 1) {
						patched = true;
						otherNode.target = target;
					}

					if (patched)
						Log.error("Patching impossible fall-through from unconditional branch 0x%x\n", opcode);
				}

				if (!patched) {
					throw new MergeException(
							"Target mismatch for branch node at index %d of 0x%x with opcode 0x%x: %d vs. %d", index,
							routineHash, opcode, target.index, ((ScriptBranchNode) other).target.index);
				}
			}
		}
	}

	@Override
	public ScriptNode copy() {
		return new ScriptBranchNode(routineHash, typeFlags, opcode, index, lineNumber, branchUserLevel);
	}
}
