package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;

class SourceWordList implements FeatureResponseGenerator.Field {

	private final float skewThreshold;

	SourceWordList(float skewThreshold) {
		this.skewThreshold = skewThreshold;
	}
	
	// label routines according to the training edge set
	// load words for those routines
	// calculate thresholds for training data (or config?)

	@Override
	public int getByteCount() {
		return 0;
	}

	@Override
	public void write(ByteBuffer buffer) {
	}

	@Override
	public void reset() {
	}
}
