package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.ScriptMerge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;

public class ScriptGraphLoader {

	public void loadGraph(ScriptGraphDataSource dataSource, ScriptFlowGraph graph, ScriptMerge.Side side)
			throws IOException {
		Log.log("Loading %s from the %s", dataSource.getType(), side);

		switch (dataSource.getType()) {
			case RUN:
				ScriptRunLoader runLoader = new ScriptRunLoader();
				runLoader.loadRun((ScriptRunFileSet) dataSource, graph, side);
				break;
			case DATASET:
				ScriptDatasetLoader datasetLoader = new ScriptDatasetLoader();
				datasetLoader.loadDataset((ScriptDatasetFile) dataSource, graph);
				break;
		}
	}
}
