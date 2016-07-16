package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptNode {

	public enum TypeFlag {
		BRANCH(0x1),
		CALL(0x2),
		EVAL(0x4);

		private int bit;

		private TypeFlag(int bit) {
			this.bit = bit;
		}

		public static int encode(Set<TypeFlag> flags) {
			int bits = 0;
			for (TypeFlag flag : flags) {
				bits |= flag.bit;
			}
			return bits;
		}

		public static Set<TypeFlag> decode(int bits) {
			Set<ScriptNode.TypeFlag> typeFlags = EnumSet.noneOf(ScriptNode.TypeFlag.class);
			for (TypeFlag flag : TypeFlag.values()) {
				if ((flag.bit & bits) == flag.bit)
					typeFlags.add(flag);
			}
			return typeFlags;
		}
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
			Log.warn("Unknown SubscriptType %d; assuming EVAL", flag);
			return SubscriptType.EVAL;
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
		ZEND_NOP(0, OpcodeTargetType.NONE),
		ZEND_CONCAT(8, OpcodeTargetType.NONE),
		ZEND_CAST(0x15, OpcodeTargetType.NONE),
		ZEND_ASSIGN_CONCAT(0x1e, OpcodeTargetType.NONE),
		ZEND_JMP(0x2a, OpcodeTargetType.REQUIRED),
		ZEND_JMPZ(0x2b, OpcodeTargetType.REQUIRED),
		ZEND_JMPNZ(0x2c, OpcodeTargetType.REQUIRED),
		ZEND_JMPZNZ(0x2d, OpcodeTargetType.REQUIRED),
		ZEND_JMPZ_EX(0x2e, OpcodeTargetType.REQUIRED),
		ZEND_JMPNZ_EX(0x2f, OpcodeTargetType.REQUIRED),
		ZEND_SEND_VAR_NO_REF_EX(0x32, OpcodeTargetType.NONE),
		ZEND_INIT_FCALL_BY_NAME(0x3b, OpcodeTargetType.NONE),
		ZEND_DO_FCALL(0x3c, OpcodeTargetType.EXTERNAL),
		ZEND_INIT_FCALL(0x3d, OpcodeTargetType.NONE),
		ZEND_RETURN(0x3e, OpcodeTargetType.NONE),
		ZEND_SEND_VAL(0x41, OpcodeTargetType.NONE),
		ZEND_SEND_VAR_EX(0x42, OpcodeTargetType.NONE),
		ZEND_SEND_REF(0x43, OpcodeTargetType.NONE),
		ZEND_NEW(0x44, OpcodeTargetType.NONE),
		ZEND_INIT_NS_FCALL_BY_NAME(0x45, OpcodeTargetType.NONE),
		ZEND_INCLUDE_OR_EVAL(0x49, OpcodeTargetType.EXTERNAL),
		ZEND_FE_RESET_R(0x4d, OpcodeTargetType.REQUIRED),
		ZEND_FE_FETCH_R(0x4e, OpcodeTargetType.REQUIRED),
		ZEND_FETCH_DIM_R(0x51, OpcodeTargetType.NONE),
		ZEND_FETCH_OBJ_R(0x52, OpcodeTargetType.EXTERNAL),
		ZEND_FETCH_DIM_W(0x54, OpcodeTargetType.NONE),
		ZEND_FETCH_OBJ_W(0x55, OpcodeTargetType.EXTERNAL),
		ZEND_FETCH_DIM_RW(0x57, OpcodeTargetType.NONE),
		ZEND_FETCH_OBJ_RW(0x58, OpcodeTargetType.EXTERNAL),
		ZEND_FETCH_DIM_IS(0x5a, OpcodeTargetType.NONE),
		ZEND_FETCH_OBJ_IS(0x5b, OpcodeTargetType.EXTERNAL),
		ZEND_FETCH_DIM_FUNC_ARG(0x5d, OpcodeTargetType.NONE),
		ZEND_FETCH_OBJ_FUNC_ARG(0x5e, OpcodeTargetType.NONE),
		ZEND_FETCH_DIM_UNSET(0x60, OpcodeTargetType.EXTERNAL),
		ZEND_FETCH_OBJ_UNSET(0x61, OpcodeTargetType.EXTERNAL),
		ZEND_FETCH_CONSTANT(0x63, OpcodeTargetType.NONE),
		ZEND_SEND_VAR_NO_REF(0x6a, OpcodeTargetType.NONE),
		ZEND_THROW(0x6c, OpcodeTargetType.NONE),
		ZEND_FETCH_CLASS(0x6d, OpcodeTargetType.NONE),
		ZEND_CATCH(0x6b, OpcodeTargetType.NULLABLE), // may branch to next catch
		ZEND_RETURN_BY_REF(0x6f, OpcodeTargetType.NONE),
		ZEND_INIT_METHOD_CALL(0x70, OpcodeTargetType.NONE),
		ZEND_INIT_STATIC_METHOD_CALL(0x71, OpcodeTargetType.NONE),
		ZEND_ISSET_ISEMPTY_DIM_OBJ(0x73, OpcodeTargetType.EXTERNAL),
		ZEND_SEND_VAL_EX(0x74, OpcodeTargetType.NONE),
		ZEND_SEND_VAR(0x75, OpcodeTargetType.NONE),
		ZEND_INIT_USER_CALL(0x76, OpcodeTargetType.NONE),
		ZEND_DO_ICALL(0x81, OpcodeTargetType.NONE),
		ZEND_ASSIGN_OBJ(0x88, OpcodeTargetType.EXTERNAL),
		ZEND_DECLARE_INHERITED_CLASS(0x8c, OpcodeTargetType.NONE),
		ZEND_ADD_INTERFACE(0x90, OpcodeTargetType.NONE),
		ZEND_ASSIGN_DIM(0x93, OpcodeTargetType.NONE),
		ZEND_ISSET_ISEMPTY_PROP_OBJ(0x94, OpcodeTargetType.EXTERNAL), // may call an accessor
		ZEND_JMP_SET(0x98, OpcodeTargetType.REQUIRED),
		ZEND_ADD_TRAIT(0x9a, OpcodeTargetType.NONE),
		ZEND_FAST_RET(0xa3, OpcodeTargetType.NONE),
		OTHER(-1, OpcodeTargetType.NONE);

		public final OpcodeTargetType targetType;
		public final int code;

		private Opcode(int code, OpcodeTargetType targetType) {
			this.code = code;
			this.targetType = targetType;
		}

		public boolean isReturn() {
			switch (this) {
				case ZEND_RETURN:
				case ZEND_RETURN_BY_REF:
				case ZEND_FAST_RET:
					return true;
			}
			return false;
		}

		public static Opcode forCode(int code) {
			for (Opcode opcode : Opcode.values()) {
				if (opcode.code == code)
					return opcode;
			}
			return OTHER;
		}
	}

	public static Set<TypeFlag> identifyTypes(int opcodeValue, int extendedValue) {
		Set<TypeFlag> flags = EnumSet.noneOf(TypeFlag.class);
		Opcode opcode = Opcode.forCode(opcodeValue);
		if (opcode != null) {
			switch (opcode) {
				case ZEND_JMP:
				case ZEND_JMPZ:
				case ZEND_JMPNZ:
				case ZEND_JMPNZ_EX:
				case ZEND_JMPZ_EX:
				case ZEND_JMPZNZ:
				case ZEND_FE_RESET_R:
				case ZEND_FE_FETCH_R:
				case ZEND_CATCH:
					flags.add(TypeFlag.BRANCH);
					break;
				default:
			}
			switch (opcode) {
				case ZEND_NOP: /* might load a class from a script top nop! */
				case ZEND_CONCAT: /* might call __toString()! */
				case ZEND_ASSIGN_CONCAT: /* might call __toString()! */
				case ZEND_CAST: /* might call __toString()! */
				case ZEND_DO_FCALL:
				case ZEND_INIT_FCALL:
				case ZEND_DO_ICALL:
				case ZEND_THROW:
				case ZEND_NEW:
				case ZEND_INIT_NS_FCALL_BY_NAME:
				case ZEND_INCLUDE_OR_EVAL:
				case ZEND_FE_RESET_R:
				case ZEND_FE_FETCH_R:
				case ZEND_FETCH_DIM_R:
				case ZEND_FETCH_OBJ_R:
				case ZEND_FETCH_DIM_W:
				case ZEND_FETCH_OBJ_W:
				case ZEND_FETCH_DIM_RW:
				case ZEND_FETCH_OBJ_RW:
				case ZEND_FETCH_DIM_IS:
				case ZEND_FETCH_OBJ_IS:
				case ZEND_FETCH_DIM_FUNC_ARG:
				case ZEND_FETCH_OBJ_FUNC_ARG:
				case ZEND_FETCH_DIM_UNSET:
				case ZEND_FETCH_OBJ_UNSET:
				case ZEND_FETCH_CONSTANT:
				case ZEND_FETCH_CLASS:
				case ZEND_INIT_METHOD_CALL:
				case ZEND_INIT_STATIC_METHOD_CALL:
				case ZEND_ISSET_ISEMPTY_DIM_OBJ:
				case ZEND_ASSIGN_OBJ:
				case ZEND_ADD_INTERFACE:
				case ZEND_ADD_TRAIT:
				case ZEND_DECLARE_INHERITED_CLASS:
				case ZEND_ASSIGN_DIM:
				case ZEND_ISSET_ISEMPTY_PROP_OBJ:
					flags.add(TypeFlag.CALL);
				default:
			}
			if (opcode == Opcode.ZEND_INCLUDE_OR_EVAL && SubscriptType.forFlag(extendedValue) == SubscriptType.EVAL)
				flags.add(TypeFlag.EVAL);
		}
		return flags;
	}

	public static boolean isCallInit(int opcode) {
		for (Opcode op : CALL_INIT_OPCODES) {
			if (op.code == opcode)
				return true;
		}
		return false;
	}

	public static boolean isOpcodeCompatible(int first, int second) {
		if (first == second)
			return true;

		Opcode firstOp = Opcode.forCode(first);
		Opcode secondOp = Opcode.forCode(second);
		if (firstOp != null && secondOp != null) {
			for (EnumSet<Opcode> compatibleSet : COMPATIBLE_OPCODE_SETS) {
				if (compatibleSet.contains(firstOp) && compatibleSet.contains(secondOp))
					return true;
			}
		}
		return false;
	}

	public static void init() {
		COMPATIBLE_OPCODE_SETS.add(EnumSet.of(Opcode.ZEND_INIT_FCALL, Opcode.ZEND_INIT_FCALL_BY_NAME));
		COMPATIBLE_OPCODE_SETS.add(EnumSet.of(Opcode.ZEND_SEND_VAL, Opcode.ZEND_SEND_VAL_EX));
		COMPATIBLE_OPCODE_SETS.add(EnumSet.of(Opcode.ZEND_SEND_VAR, Opcode.ZEND_SEND_VAR_EX, Opcode.ZEND_SEND_REF,
				Opcode.ZEND_SEND_VAR_NO_REF, Opcode.ZEND_SEND_VAR_NO_REF_EX));
		COMPATIBLE_OPCODE_SETS.add(EnumSet.of(Opcode.ZEND_FETCH_DIM_R, Opcode.ZEND_FETCH_DIM_FUNC_ARG));
		COMPATIBLE_OPCODE_SETS.add(EnumSet.of(Opcode.ZEND_NOP, Opcode.ZEND_FETCH_CLASS,
				Opcode.ZEND_DECLARE_INHERITED_CLASS));
	}

	public static final List<EnumSet<Opcode>> COMPATIBLE_OPCODE_SETS = new ArrayList<EnumSet<Opcode>>();

	public static final EnumSet<Opcode> CALL_INIT_OPCODES = EnumSet.of(Opcode.ZEND_INIT_FCALL,
			Opcode.ZEND_INIT_FCALL_BY_NAME, Opcode.ZEND_INIT_METHOD_CALL, Opcode.ZEND_INIT_NS_FCALL_BY_NAME,
			Opcode.ZEND_INIT_STATIC_METHOD_CALL, Opcode.ZEND_INIT_USER_CALL);

	public static final int USER_LEVEL_TOP = 0x3f;

	public final int routineHash;
	public final Set<TypeFlag> typeFlags;
	public final int opcode; // TODO: use Opcode
	public final int lineNumber;
	public final int index;

	private ScriptNode next;
	private int nodeUserLevel = USER_LEVEL_TOP;

	private List<RoutineExceptionEdge> thrownExceptions = new ArrayList<RoutineExceptionEdge>();

	public ScriptNode(int routineHash, Set<TypeFlag> typeFlags, int opcode, int lineNumber, int index) {
		this.routineHash = routineHash;
		this.typeFlags = typeFlags;
		this.opcode = opcode;
		this.lineNumber = lineNumber;
		this.index = index;
	}

	public ScriptNode copy() {
		return new ScriptNode(routineHash, typeFlags, opcode, lineNumber, index);
	}

	public ScriptNode getNext() {
		return next;
	}

	public void setNext(ScriptNode next) {
		this.next = next;
	}

	public int getNodeUserLevel() {
		return nodeUserLevel;
	}

	public void setNodeUserLevel(int userLevel) {
		this.nodeUserLevel = userLevel;
	}

	public void verifyEqual(ScriptNode other) {
		if (index != other.index) {
			throw new MergeException("Matching nodes have differing index: %d vs. %d", index, other.index);
		}
		if (!isOpcodeCompatible(opcode, other.opcode)) {
			throw new MergeException("Matching nodes at index %d have incompatible opcodes: 0x%x vs. 0x%x", index,
					opcode, other.opcode);
		}
	}

	public void verifyCompatible(ScriptNode other) {
		verifyEqual(other);
	}

	public boolean isEqual(ScriptNode other) {
		return (index == other.index && opcode == other.opcode);
	}

	public void addThrownException(RoutineExceptionEdge throwEdge) {
		thrownExceptions.add(throwEdge);
	}

	public Iterable<RoutineExceptionEdge> getThrownExceptions() {
		return thrownExceptions;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + index;
		result = prime * result + routineHash;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ScriptNode other = (ScriptNode) obj;
		if (index != other.index)
			return false;
		if (routineHash != other.routineHash)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("0x%x", opcode);
	}
}
