package edu.uci.eecs.scriptsafe.analysis.dictionary;

import java.io.File;
import java.util.Collection;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;

public class CoverageMap {

	public static final OptionArgumentMap.StringOption datasetDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.StringOption phpDir = OptionArgumentMap.createStringOption('s');
	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final RoutineLineMap routineLineMap = new RoutineLineMap();

	private ScriptFlowGraph sourceGraph;
	private File outputFile;

	private int serverPort;

	private CoverageMap(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, datasetDir, phpDir, verbose, watchlistFile, watchlistCategories);
	}

	private void countLines() {
		ScriptNode.init();

		argMap.parseOptions();

		Log.addOutput(System.out);
		Log.setLevel(Log.Level.values()[verbose.getValue()]);
		System.out.println("Log level " + verbose.getValue());

		try {
			if (!(datasetDir.hasValue() && phpDir.hasValue())) {
				printUsage();
				return;
			}

			File datasetDirectory = new File(datasetDir.getValue());
			File phpDirectory = new File(phpDir.getValue());
			Collection<ApplicationFile> lineCounts = routineLineMap.countLines(
					ScriptDataFilename.ROUTINE_CATALOG.requireFile(datasetDirectory), phpDirectory,
					ScriptDataFilename.CFG.requireFile(datasetDirectory));

			// TODO: report the lineCounts
		} catch (Throwable t) {
			Log.error("Uncaught %s exception:", t.getClass().getSimpleName());
			Log.log(t);
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -d <dataset-dir> -s <php-src-dir>", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		try {
			ArgumentStack stack = new ArgumentStack(args);
			CoverageMap map = new CoverageMap(stack);
			map.countLines();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
