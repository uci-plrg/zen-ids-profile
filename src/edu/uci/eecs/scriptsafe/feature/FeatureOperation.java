package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;

import edu.uci.eecs.scriptsafe.analysis.AnalysisException;

public enum FeatureOperation {
	TRAIN_ON_K,
	GET_FEATURES,
	GET_EDGE_LABEL,
	GET_GRAPH_PROPERTIES;

	static final int OPERATION_BYTE_COUNT = 12;

	public final byte code;

	private FeatureOperation() {
		this.code = (byte) ordinal();
	}

	private static final ByteBuffer intBuffer = ByteBuffer.allocate(4);

	static FeatureOperation forByte(byte b) {
		for (FeatureOperation o : values()) {
			if (o.code == b)
				return o;
		}
		throw new AnalysisException("Cannot understand op with opcode %d", b);
	}

	static ByteBuffer create(FeatureOperation op, int frommRoutineHash, int fromOpcode, int toRoutineHash) {
		ByteBuffer opBytes = ByteBuffer.allocate(OPERATION_BYTE_COUNT);
		opBytes.put(op.code);
		opBytes.putInt(frommRoutineHash);
		opBytes.putShort((short) fromOpcode);
		opBytes.putInt(toRoutineHash);
		opBytes.rewind();
		return opBytes;
	}
}
