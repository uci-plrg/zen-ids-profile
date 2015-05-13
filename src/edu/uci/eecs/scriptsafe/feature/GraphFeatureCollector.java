package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;
import java.util.Map;

public class GraphFeatureCollector {

	private FeatureDataSource dataSource;

	private final FeatureRoleCounts totalRoutines = new FeatureRoleCounts();

	final FeatureResponseGenerator responseGenerator = new FeatureResponseGenerator();

	void setDataSource(FeatureDataSource dataSource) {
		this.dataSource = dataSource;

		responseGenerator.addField(totalRoutines);
		responseGenerator.addField(dataSource.wordList.createNonEmptyRoutineResponseField()); // updated on k cycle
	}

	ByteBuffer getFeatures() {
		responseGenerator.resetAllFields();

		for (Map.Entry<Integer, Integer> entry : dataSource.requestGraph.calledRoutineUserLevel.entrySet())
			totalRoutines.increment(entry.getValue() >= 2);

		return responseGenerator.generateResponse();
	}
}
