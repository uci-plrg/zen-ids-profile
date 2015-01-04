package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptGraphLoader {

	public ScriptGraphLoader() {
	}

	public ScriptFlowGraph loadGraph(ScriptGraphDataSource dataSource) throws IOException {
		ScriptFlowGraph graph = new ScriptFlowGraph();
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
				loadDataset((ScriptDatasetFile) dataSource, graph);
				break;
		}
	}

	private void loadDataset(ScriptDatasetFile dataset, ScriptFlowGraph graph) throws IOException {

	}
}
