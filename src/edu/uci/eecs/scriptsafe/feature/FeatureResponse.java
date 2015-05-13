package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;

public enum FeatureResponse {

	OK,
	ERROR;

	final byte code;

	private FeatureResponse() {
		code = (byte) ordinal();
	}

	ByteBuffer generateResponse() {
		return ByteBuffer.allocate(1).put((byte) code);
	}
}
