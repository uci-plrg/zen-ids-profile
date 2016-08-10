package edu.uci.plrg.cfi.php.feature;

import java.io.IOException;
import java.nio.ByteBuffer;

import edu.uci.plrg.cfi.common.log.Log;

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
			Log.log("Edge delta: %d", delta.remaining() / 11);

			int end = service.getDataSource().trainingRequestGraph.getNumberOfSets();
			for (int k = 0; k < (end - 1); k++) {
				Log.log("   ----- k = %d -----\n", k);
				ByteBuffer trainCommand = FeatureOperation.create(FeatureOperation.TRAIN_ON_K, k, 0, 0);
				ByteBuffer training = service.execute(trainCommand);
				FeatureResponse.checkStatus(training);

				ByteBuffer graphPropertiesCommand = FeatureOperation.create(FeatureOperation.GET_GRAPH_PROPERTIES, 0,
						0, 0);
				ByteBuffer graphProperties = service.execute(graphPropertiesCommand);
				FeatureResponse.checkStatus(graphProperties);
				Log.log("Graph has %d/%d routines (%d/%d non-empty) and %d/%d edges", graphProperties.getInt(),
						graphProperties.getInt(), graphProperties.getInt(), graphProperties.getInt(),
						graphProperties.getInt(), graphProperties.getInt());

				deltaCommand = FeatureOperation.create(FeatureOperation.GET_K_DELTA, k + 1, 0, 0);
				delta = service.execute(deltaCommand);
				Log.log("Edge delta: %d", delta.remaining() / 11);
				getFeatures(delta);
			}
		} catch (Throwable t) {
			Log.error("%s during execution of %s", t.getClass().getSimpleName(), getClass().getSimpleName());
			Log.log(t);
		}
	}

	private void getFeatures(ByteBuffer delta) throws IOException {
		FeatureResponse.checkStatus(delta);

		int fromRoutineHash, fromIndex, toRoutineHash;
		boolean isAdmin;
		ByteBuffer featureCommand, features;
		while (delta.remaining() > 0) {
			fromRoutineHash = delta.getInt();
			fromIndex = delta.getShort();
			toRoutineHash = delta.getInt();
			isAdmin = delta.get() > 0;

			featureCommand = FeatureOperation.create(FeatureOperation.GET_FEATURES, fromRoutineHash, fromIndex,
					toRoutineHash);
			features = service.execute(featureCommand);
			FeatureResponse.checkStatus(features);

			Log.log("0x%08x:%04d -> 0x%08x %10s: call site %d/%d | calling sites %d/%d", fromRoutineHash, fromIndex,
					toRoutineHash, isAdmin ? "admin" : "anonymous", features.getInt(), features.getInt(),
					features.getInt(), features.getInt());
			Log.log("%41s target %d/%d | file %d/%d | directory %d/%d", "", features.getInt(), features.getInt(),
					features.getInt(), features.getInt(), features.getInt(), features.getInt());
		}
	}
}
