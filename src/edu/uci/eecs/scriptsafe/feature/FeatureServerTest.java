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
			FeatureResponse.checkStatus(delta);
			Log.log("Edge delta at k=%d: %d", -1, delta.remaining() / 11);

			int end = service.getDataSource().requestGraph.getNumberOfSets();
			for (int k = 0; k < (end - 1); k++) {
				Log.log("   ===== k = %d =====", k);
				ByteBuffer trainCommand = FeatureOperation.create(FeatureOperation.TRAIN_ON_K, k, 0, 0);
				ByteBuffer training = service.execute(trainCommand);
				FeatureResponse.checkStatus(training);

				ByteBuffer graphPropertiesCommand = FeatureOperation.create(FeatureOperation.GET_GRAPH_PROPERTIES, 0,
						0, 0);
				ByteBuffer graphProperties = service.execute(graphPropertiesCommand);
				FeatureResponse.checkStatus(graphProperties);
				Log.log("Graph has %d/%d total routines and %d/%d non-empty routines", graphProperties.getInt(),
						graphProperties.getInt(), graphProperties.getInt(), graphProperties.getInt());

				deltaCommand = FeatureOperation.create(FeatureOperation.GET_K_DELTA, k + 1, 0, 0);
				delta = service.execute(deltaCommand);
				FeatureResponse.checkStatus(delta);
				Log.log("Edge delta: %d", delta.remaining() / 11);
			}
		} catch (Throwable t) {
			Log.error("%s during execution of %s", t.getClass().getSimpleName(), getClass().getSimpleName());
			Log.log(t);
		}
	}
}
