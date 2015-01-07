package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;

import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;

public class ScriptGraphLoader {

	public ScriptGraphLoader() {
	}

	public ScriptFlowGraph loadGraph(ScriptGraphDataSource dataSource) throws IOException {
		ScriptFlowGraph graph = new ScriptFlowGraph(dataSource.getDescription());
		loadGraph(dataSource, graph);
		return graph;
	}

	public void loadGraph(ScriptGraphDataSource dataSource, ScriptFlowGraph graph) throws IOException {
		switch (dataSource.getType()) {
			case RUN:
				ScriptRunLoader runLoader = new ScriptRunLoader();
				runLoader.loadRun((ScriptRunFileSet) dataSource, graph);
				break;
			case DATASET:
				ScriptDatasetLoader datasetLoader = new ScriptDatasetLoader();
				datasetLoader.loadDataset((ScriptDatasetFile) dataSource, graph);
				break;
		}
	}
}
