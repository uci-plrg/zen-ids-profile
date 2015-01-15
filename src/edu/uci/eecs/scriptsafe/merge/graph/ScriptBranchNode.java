package edu.uci.eecs.scriptsafe.merge.graph;

import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptBranchNode extends ScriptNode {

	public static final int UNASSIGNED_BRANCH_TARGET_ID = -1;

	private ScriptNode target = null;

	public ScriptBranchNode(int opcode, int index) {
		super(Type.BRANCH, opcode, index);
	}

	public void setTarget(ScriptNode target) {
		this.target = target;
	}

	public ScriptNode getTarget() {
		return target;
	}

	public int getTargetIndex() {
		if (target == null) {
			switch (Opcode.forCode(opcode)) {
				case ZEND_BRK:
				case ZEND_CONT:
					return -1;
			}
			throw new MergeException("Target missing for branch node with opcode 0x%x", opcode);
		}
		return target.index;
	}

	@Override
	public void verifyEqual(ScriptNode other) {
		super.verifyEqual(other);

		switch (Opcode.forCode(opcode)) {
			case ZEND_BRK:
			case ZEND_CONT:
				if (target == null || ((ScriptBranchNode) other).target == null)
					return;
		}

		if (target.index != ((ScriptBranchNode) other).target.index)
			throw new MergeException("Target mismatch for branch node at index %d", index);
	}

	@Override
	public ScriptNode copy() {
		return new ScriptBranchNode(opcode, index);
	}
}
