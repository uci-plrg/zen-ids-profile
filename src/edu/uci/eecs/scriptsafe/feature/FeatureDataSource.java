package edu.uci.eecs.scriptsafe.feature;

import java.io.File;
import java.io.IOException;

import edu.uci.eecs.scriptsafe.analysis.dictionary.RoutineLineMap;
import edu.uci.eecs.scriptsafe.analysis.request.CrossValidationRequestGraph;
import edu.uci.eecs.scriptsafe.analysis.request.RequestGraphLoader;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;

public class FeatureDataSource {

	private final RequestGraphLoader requestLoader = new RequestGraphLoader();
	private final ScriptDatasetLoader datasetLoader = new ScriptDatasetLoader();

	final RoutineLineMap routineLineMap = new RoutineLineMap();
	final ScriptFlowGraph dataset;
	final CrossValidationRequestGraph requestGraph;

	public FeatureDataSource(String datasetDir, String phpDir, FeatureCrossValidationSets crossValidationSets)
			throws IOException {
		File datasetDirectory = new File(datasetDir);
		File phpDirectory = new File(phpDir);
		File datasetFile = ScriptDataFilename.CFG.requireFile(datasetDirectory);
		File routineCatalogFile = ScriptDataFilename.ROUTINE_CATALOG.requireFile(datasetDirectory);
		dataset = new ScriptFlowGraph(Type.DATASET, datasetFile.getAbsolutePath(), false);
		datasetLoader.loadDataset(datasetFile, routineCatalogFile, dataset);
		routineLineMap.load(routineCatalogFile, phpDirectory, datasetFile);
		requestLoader.addPath(datasetDirectory.toPath());
		requestGraph = new CrossValidationRequestGraph(crossValidationSets);
		requestLoader.load(requestGraph);
	}
}
