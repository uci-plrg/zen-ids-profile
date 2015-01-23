package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

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

	public enum OpcodeTargetType {
		NONE,
		NULLABLE,
		DYNAMIC,
		EXTERNAL,
		REQUIRED
	}

	public enum Opcode {
		ZEND_JMP(0x2a),
		ZEND_JMPZ(0x2b),
		ZEND_JMPNZ(0x2c),
		ZEND_JMPZNZ(0x2d),
		ZEND_JMPZ_EX(0x2e),
		ZEND_JMPNZ_EX(0x2f),
		ZEND_BRK(0x32, OpcodeTargetType.DYNAMIC),
		ZEND_CONT(0x33, OpcodeTargetType.DYNAMIC),
		ZEND_DO_FCALL(0x3c, OpcodeTargetType.EXTERNAL),
		ZEND_NEW(0x44, OpcodeTargetType.NONE),
		ZEND_INCLUDE_OR_EVAL(0x49, OpcodeTargetType.EXTERNAL),
		ZEND_FE_RESET(0x4d),
		ZEND_FE_FETCH(0x4e),
		ZEND_CATCH(0x6b, OpcodeTargetType.NULLABLE), // may branch to next catch
		ZEND_JMP_SET(0x98),
		ZEND_ASSIGN_DIM(0x93, OpcodeTargetType.NONE),
		OTHER(-1);

		public final OpcodeTargetType targetType;
		public final int code;

		private Opcode(int code) {
			this(code, OpcodeTargetType.REQUIRED);
		}

		private Opcode(int code, OpcodeTargetType targetType) {
			this.code = code;
			this.targetType = targetType;
		}

		public static Opcode forCode(int code) {
			for (Opcode opcode : Opcode.values()) {
				if (opcode.code == code)
					return opcode;
			}
			return OTHER;
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
					return Type.NORMAL;
				case ZEND_JMP:
				case ZEND_JMPZ:
				case ZEND_JMPNZ:
				case ZEND_JMPNZ_EX:
				case ZEND_JMPZ_EX:
				case ZEND_JMPZNZ:
				case ZEND_BRK:
				case ZEND_CONT:
				case ZEND_FE_RESET:
				case ZEND_FE_FETCH:
				case ZEND_CATCH:
					return Type.BRANCH;
			}
		}
		return Type.NORMAL;
	}

	public final Type type;
	public final int opcode;
	public final int index;

	private ScriptNode next;

	private List<RoutineExceptionEdge> thrownExceptions = new ArrayList<RoutineExceptionEdge>();

	public ScriptNode(Type type, int opcode, int index) {
		this.type = type;
		this.opcode = opcode;
		this.index = index;
	}

	public ScriptNode copy() {
		return new ScriptNode(type, opcode, index);
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

	public void addThrownException(RoutineExceptionEdge throwEdge) {
		thrownExceptions.add(throwEdge);
	}

	public Iterable<RoutineExceptionEdge> getThrownExceptions() {
		return thrownExceptions;
	}

	@Override
	public String toString() {
		return String.format("0x%x", opcode);
	}
}
