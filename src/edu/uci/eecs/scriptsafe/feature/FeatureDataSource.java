package edu.uci.eecs.scriptsafe.feature;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import edu.uci.eecs.scriptsafe.analysis.dictionary.Dictionary;
import edu.uci.eecs.scriptsafe.analysis.dictionary.RoutineLineMap;
import edu.uci.eecs.scriptsafe.analysis.dictionary.SkewDictionary;
import edu.uci.eecs.scriptsafe.analysis.request.RequestGraph;
import edu.uci.eecs.scriptsafe.analysis.request.RequestGraphLoader;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;

public class FeatureDataSource {

	private final RequestGraphLoader requestLoader = new RequestGraphLoader();
	private final ScriptDatasetLoader datasetLoader = new ScriptDatasetLoader();

	final RoutineLineMap routineLineMap = new RoutineLineMap();
	final RequestGraph requestGraph;
	final ScriptFlowGraph dataset;
	final Dictionary dictionary;

	public FeatureDataSource(String datasetDir, String phpDir, Set<Integer> requestFilter) throws IOException {
		File datasetDirectory = new File(datasetDir);
		File phpDirectory = new File(phpDir);
		File datasetFile = ScriptDataFilename.CFG.requireFile(datasetDirectory);
		File routineCatalogFile = ScriptDataFilename.ROUTINE_CATALOG.requireFile(datasetDirectory);
		dataset = new ScriptFlowGraph(Type.DATASET, datasetFile.getAbsolutePath(), false);
		datasetLoader.loadDataset(datasetFile, routineCatalogFile, dataset);
		routineLineMap.load(routineCatalogFile, phpDirectory, datasetFile);
		dictionary = new SkewDictionary(routineLineMap);
		requestLoader.addPath(datasetDirectory.toPath());
		requestLoader.setRequestFilter(requestFilter);
		requestGraph = requestLoader.load();
	}
}
