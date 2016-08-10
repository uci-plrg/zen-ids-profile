package edu.uci.plrg.cfi.php.merge.graph.loader;

import java.io.IOException;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.php.merge.DatasetMerge;
import edu.uci.plrg.cfi.php.merge.graph.ScriptFlowGraph;

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
				datasetLoader.loadDataset(files.dataset, files.routineCatalog, graph, shallow);
				break;
		}
	}
}
