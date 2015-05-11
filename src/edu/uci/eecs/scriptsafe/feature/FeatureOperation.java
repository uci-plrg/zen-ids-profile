package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;

import edu.uci.eecs.scriptsafe.analysis.AnalysisException;

public enum FeatureOperation {
	GET_FEATURES((byte) 0),
	GET_EDGE_LABEL((byte) 1),
	GET_GRAPH_PROPERTIES((byte) 2);

	static final int OPERATION_BYTE_COUNT = 12;
	
	public final byte code;

	private FeatureOperation(byte code) {
		this.code = code;
	}

	private static final ByteBuffer intBuffer = ByteBuffer.allocate(4);

	static FeatureOperation forByte(byte b) {
		for (FeatureOperation o : values()) {
			if (o.code == b)
				return o;
		}
		throw new AnalysisException("Cannot understand op with opcode %d", b);
	}

	static byte[] create(FeatureOperation op, int frommRoutineHash, int fromOpcode, int toRoutineHash) {
		ByteBuffer opBytes = ByteBuffer.allocate(OPERATION_BYTE_COUNT);
		opBytes.put(op.code);
		opBytes.putInt(frommRoutineHash);
		opBytes.putShort((short)fromOpcode);
		opBytes.putInt(toRoutineHash);
		return opBytes.array();
	}
}
