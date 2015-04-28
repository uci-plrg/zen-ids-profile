package edu.uci.eecs.scriptsafe.merge.main;

import java.io.File;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.merge.CatalogMerge;
import edu.uci.eecs.scriptsafe.merge.DatasetMerge;
import edu.uci.eecs.scriptsafe.merge.RequestMerge;
import edu.uci.eecs.scriptsafe.merge.ScriptDatasetGenerator;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptGraphCloner;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetFiles;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphLoader;

public class ScriptSafeMerge {

	public static final OptionArgumentMap.StringOption leftGraphDir = OptionArgumentMap.createStringOption('l');
	public static final OptionArgumentMap.StringOption rightGraphDir = OptionArgumentMap.createStringOption('r');
	public static final OptionArgumentMap.StringOption outputDir = OptionArgumentMap.createStringOption('o');
	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final ScriptGraphLoader loader = new ScriptGraphLoader();

	private ScriptGraphDataFiles leftDataSource;
	private ScriptGraphDataFiles rightDataSource;
	private ScriptFlowGraph leftGraph;
	private ScriptFlowGraph rightGraph;

	private ScriptSafeMerge(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, leftGraphDir, rightGraphDir, outputDir, verbose, watchlistFile,
				watchlistCategories);
	}

	private void run() {
		try {
			ScriptNode.init();

			argMap.parseOptions();

			Log.addOutput(System.out);
			Log.setLevel(Log.Level.values()[verbose.getValue()]);
			System.out.println("Log level " + verbose.getValue());

			if (!leftGraphDir.hasValue() || !rightGraphDir.hasValue() || !outputDir.hasValue()) {
				printUsage();
				return;
			}

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}

			File leftPath = new File(leftGraphDir.getValue());
			File rightPath = new File(rightGraphDir.getValue());
			leftDataSource = ScriptGraphDataFiles.Factory.bind(leftPath);
			rightDataSource = ScriptGraphDataFiles.Factory.bind(rightPath);

			rightGraph = new ScriptFlowGraph(rightDataSource.getType(), rightDataSource.getDescription(), false);
			loader.loadGraph(rightDataSource, rightGraph, DatasetMerge.Side.RIGHT);
			if (rightDataSource.getType() == Type.DATASET) {
				ScriptGraphCloner cloner = new ScriptGraphCloner();
				leftGraph = cloner.copyRoutines(rightGraph, new ScriptFlowGraph(leftDataSource.getType(),
						leftDataSource.getDescription(), true));
				loader.loadGraph(leftDataSource, leftGraph, DatasetMerge.Side.LEFT);
			} else {
				leftGraph = new ScriptFlowGraph(leftDataSource.getType(), leftDataSource.getDescription(), false);
				loader.loadGraph(leftDataSource, leftGraph, DatasetMerge.Side.LEFT);
			}

			Log.log("Left graph is a %s from %s with %d routines", leftDataSource.getClass().getSimpleName(),
					leftPath.getAbsolutePath(), leftGraph.getRoutineCount());
			Log.log("Right graph is a %s from %s with %d routines", rightDataSource.getClass().getSimpleName(),
					rightPath.getAbsolutePath(), rightGraph.getRoutineCount());

			DatasetMerge datasetMerge = new DatasetMerge(leftGraph, rightGraph,
					rightDataSource.getType() == Type.DATASET);
			datasetMerge.merge();

			Log.log("Merged graph is a %s with %d routines (%d eval routines)",
					datasetMerge.getClass().getSimpleName(), datasetMerge.getRoutineCount(),
					datasetMerge.getDynamicRoutineCount());

			ScriptDatasetFiles outputFiles = ScriptGraphDataFiles.Factory.construct(new File(outputDir.getValue()));
			ScriptDatasetGenerator datasetGenerator = new ScriptDatasetGenerator(datasetMerge, outputFiles.dataset);
			datasetGenerator.generateDataset();

			CatalogMerge catalogMerge = new CatalogMerge(leftDataSource.getRoutineCatalogFile(),
					rightDataSource.getRoutineCatalogFile());
			catalogMerge.merge();
			catalogMerge.generateCatalog(outputFiles.getRoutineCatalogFile());

			RequestMerge requestMerge = new RequestMerge(leftDataSource, rightDataSource);
			requestMerge.merge(outputFiles);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -l <left-data-source> -r <right-data-source> -o <output-dir>",
				getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		ScriptSafeMerge merge = new ScriptSafeMerge(stack);
		merge.run();
	}
}
