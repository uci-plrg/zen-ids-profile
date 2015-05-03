package edu.uci.eecs.scriptsafe.analysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.analysis.RequestGraph.CallSite;
import edu.uci.eecs.scriptsafe.analysis.RequestGraph.Edge;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetFiles;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles;

public class DictionaryGenerator {

	public static final OptionArgumentMap.StringOption datasetDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.StringOption phpDir = OptionArgumentMap.createStringOption('p');
	// public static final OptionArgumentMap.StringOption outputDir = OptionArgumentMap.createStringOption('o');
	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final RoutineLineMap routineLineMap = new RoutineLineMap();

	private final RequestGraph.Loader requestLoader = new RequestGraph.Loader();
	private RequestGraph requestGraph;

	private ScriptFlowGraph sourceGraph;
	private File outputFile;

	private DictionaryGenerator(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, datasetDir, phpDir, verbose, watchlistFile, watchlistCategories);
	}

	private void run() {
		try {
			ScriptNode.init();

			argMap.parseOptions();

			Log.addOutput(System.out);
			Log.setLevel(Log.Level.values()[verbose.getValue()]);
			System.out.println("Log level " + verbose.getValue());

			if (!(datasetDir.hasValue() && phpDir.hasValue())) {
				printUsage();
				return;
			}

			File datasetDirectory = new File(datasetDir.getValue());
			File phpDirectory = new File(phpDir.getValue());
			routineLineMap.load(new File(datasetDirectory, "routine-catalog.tab"), phpDirectory, new File(
					datasetDirectory, "cfg.set"));

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}
			
			Log.log("%s", routineLineMap.toString());

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: -d <dataset-dir> -p <php-src-dir>", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		DictionaryGenerator exporter = new DictionaryGenerator(stack);
		exporter.run();
	}
}
