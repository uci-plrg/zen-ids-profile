package edu.uci.plrg.cfi.php.feature;

import java.nio.ByteBuffer;

import edu.uci.plrg.cfi.php.analysis.AnalysisException;

public enum FeatureResponse {
	OK,
	ERROR;

	final byte code;

	private FeatureResponse() {
		code = (byte) ordinal();
	}

	public ByteBuffer generateResponse() {
		return ByteBuffer.allocate(1).put((byte) code);
	}

	public ByteBuffer generateResponse(int byteCount) {
		return ByteBuffer.allocate(1 + byteCount).put((byte) code);
	}

	public static void checkStatus(ByteBuffer response) {
		if (response.get() != OK.code)
			throw new AnalysisException("Error in response");
	}
}
