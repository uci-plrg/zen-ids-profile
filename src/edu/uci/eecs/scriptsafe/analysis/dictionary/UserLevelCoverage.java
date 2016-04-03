package edu.uci.eecs.scriptsafe.analysis.dictionary;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;

public class UserLevelCoverage {

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

	private UserLevelCoverage(ArgumentStack args) {
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
			Collection<ApplicationFile> files = routineLineMap.analyzeFiles(
					ScriptDataFilename.ROUTINE_CATALOG.requireFile(datasetDirectory), phpDirectory,
					ScriptDataFilename.CFG.requireFile(datasetDirectory));

			int anonymousLineTotal = 0, adminLineTotal = 0;
			Map<String, Integer> anonymousLineCounts = new TreeMap<String, Integer>();
			Map<String, Integer> adminLineCounts = new TreeMap<String, Integer>();
			for (ApplicationFile file : files) {
				int anonymousLineCount = 0, adminLineCount = 0;
				for (Integer userLevel : file.userLevelCoverage) {
					if (userLevel < 2)
						anonymousLineCount++;
					else
						adminLineCount++;
				}
				anonymousLineCounts.put(file.phpFile.getAbsolutePath(), anonymousLineCount);
				adminLineCounts.put(file.phpFile.getAbsolutePath(), adminLineCount + anonymousLineCount);
				anonymousLineTotal += anonymousLineCount;
				adminLineTotal += adminLineCount + anonymousLineCount;
			}

			for (Map.Entry<String, Integer> entry : anonymousLineCounts.entrySet()) {
				Log.log("%05d %05d %s", entry.getValue(), adminLineCounts.get(entry.getKey()), entry.getKey());
			}
			Log.log("%05d %05d Total", anonymousLineTotal, adminLineTotal);
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
			UserLevelCoverage map = new UserLevelCoverage(stack);
			map.countLines();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
