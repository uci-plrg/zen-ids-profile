package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;

import edu.uci.eecs.crowdsafe.common.log.Log;

class FeatureServerTest {

	private final FeatureService service;

	FeatureServerTest(FeatureService service) {
		this.service = service;
	}

	void run() {
		try {
			int k = service.crossValidationSets.crossValidationGroups.size();
			for (int i = 0; i < (k - 1); i++) {
				ByteBuffer train = FeatureOperation.create(FeatureOperation.TRAIN_ON_K, i, 0, 0);
				service.execute(train);
			}
		} catch (Throwable t) {
			Log.error("%s during execution of %s", t.getClass().getSimpleName(), getClass().getSimpleName());
			Log.log(t);
		}
	}
}
