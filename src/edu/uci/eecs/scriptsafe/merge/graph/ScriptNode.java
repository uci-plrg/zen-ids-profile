package edu.uci.eecs.scriptsafe.merge.graph;

import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptNode {

	public enum Type {
		NORMAL,
		BRANCH,
		CALL,
		EVAL;
	}

	private enum SubscriptType {
		EVAL(1),
		INCLUDE(2),
		INCLUDE_ONCE(4),
		REQUIRE(8),
		REQUIRE_ONCE(0x10);

		final int flag;

		private SubscriptType(int flag) {
			this.flag = flag;
		}

		static SubscriptType forFlag(int flag) {
			for (SubscriptType type : values()) {
				if (type.flag == flag)
					return type;
			}
			throw new IllegalArgumentException("Unknown SubscriptType " + flag);
		}
	}

	private enum Opcode {
		ZEND_JMP(42),
		ZEND_JMPZ(43),
		ZEND_JMPNZ(44),
		ZEND_JMPZNZ(45),
		ZEND_JMPZ_EX(46),
		ZEND_JMPNZ_EX(47),
		ZEND_DO_FCALL(60),
		ZEND_INCLUDE_OR_EVAL(73),
		ZEND_FAST_CALL(162);

		final int code;

		private Opcode(int code) {
			this.code = code;
		}

		static Opcode forCode(int code) {
			for (Opcode opcode : Opcode.values()) {
				if (opcode.code == code)
					return opcode;
			}
			return null;
		}
	}

	public static Type identifyType(int opcodeValue, int extendedValue) {
		Opcode opcode = Opcode.forCode(opcodeValue);
		if (opcode != null) {
			switch (opcode) {
				case ZEND_INCLUDE_OR_EVAL:
					if (SubscriptType.forFlag(extendedValue) == SubscriptType.EVAL)
						return Type.EVAL;
					else
						return Type.CALL;
				case ZEND_DO_FCALL:
				case ZEND_FAST_CALL:
					return Type.CALL;
				case ZEND_JMP:
				case ZEND_JMPZ:
				case ZEND_JMPNZ:
				case ZEND_JMPNZ_EX:
				case ZEND_JMPZ_EX:
				case ZEND_JMPZNZ:
					return Type.BRANCH;
			}
		}
		return Type.NORMAL;
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
