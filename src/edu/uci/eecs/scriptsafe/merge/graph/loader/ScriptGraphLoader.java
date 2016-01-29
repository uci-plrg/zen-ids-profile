package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.DatasetMerge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;

public class ScriptGraphLoader {

	public void loadGraph(ScriptGraphDataFiles dataSource, ScriptFlowGraph graph, DatasetMerge.Side side,
			boolean shallow) throws IOException {
		Log.log("Loading %s from the %s", dataSource.getType(), side);

		switch (dataSource.getType()) {
			case RUN:
				ScriptRunLoader runLoader = new ScriptRunLoader();
				runLoader.loadRun((ScriptRunFiles) dataSource, graph, side, shallow);
				break;
			case DATASET:
				ScriptDatasetLoader datasetLoader = new ScriptDatasetLoader();
				ScriptDatasetFiles files = (ScriptDatasetFiles) dataSource;
				datasetLoader.loadDataset(files.dataset, files.routineCatalog, graph);
				break;
		}
	}
}
