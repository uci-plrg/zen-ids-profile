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
			ByteBuffer deltaCommand = FeatureOperation.create(FeatureOperation.GET_K_DELTA, 0, 0, 0);
			ByteBuffer delta = service.execute(deltaCommand);
			Log.log("Edge delta at k=%d: %d", -1, delta.remaining() / 11);

			int end = service.getDataSource().requestGraph.getNumberOfSets();
			for (int k = 0; k < (end - 1); k++) {
				ByteBuffer trainCommand = FeatureOperation.create(FeatureOperation.TRAIN_ON_K, k, 0, 0);
				service.execute(trainCommand);

				deltaCommand = FeatureOperation.create(FeatureOperation.GET_K_DELTA, k + 1, 0, 0);
				delta = service.execute(deltaCommand);
				Log.log("Edge delta at k=%d: %d", k, delta.remaining() / 11);
			}
		} catch (Throwable t) {
			Log.error("%s during execution of %s", t.getClass().getSimpleName(), getClass().getSimpleName());
			Log.log(t);
		}
	}
}
