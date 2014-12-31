package edu.uci.eecs.scriptsafe.merge.graph;

import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptNode {

	public enum Type {
		NORMAL,
		BRANCH,
		CALL,
		EVAL
	}

	public final Type type;
	public final int opcode;
	public final int index;

	private ScriptNode next;

	ScriptNode(Type type, int opcode, int index) {
		this.type = type;
		this.opcode = opcode;
		this.index = index;
	}

	public ScriptNode(int opcode, int index) {
		this(Type.NORMAL, opcode, index);
	}

	public ScriptNode copy() {
		return new ScriptNode(opcode, index);
	}

	public ScriptNode getNext() {
		return next;
	}

	public void setNext(ScriptNode next) {
		this.next = next;
	}

	public void verifyEqual(ScriptNode other) {
		if (type != other.type) {
			throw new MergeException("Matching nodes have differing type: %s vs. %s", type, other.type);
		}
		if (index != other.index) {
			throw new MergeException("Matching nodes have differing index: %d vs. %d", index, other.index);
		}
		if (opcode != other.opcode) {
			throw new MergeException("Matching nodes at index %d have differing opcodes: %d vs. %d", index, opcode,
					other.opcode);
		}
	}

	public boolean isEqual(ScriptNode other) {
		return (type == other.type && index == other.index && opcode == other.opcode);
	}
}
