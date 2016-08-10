package edu.uci.plrg.cfi.php.analysis.dictionary;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.ArgumentStack;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap.OptionMode;
import edu.uci.plrg.cfi.php.merge.graph.ScriptDataFilename;
import edu.uci.plrg.cfi.php.merge.graph.ScriptFlowGraph;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode;

public class UserLevelCoverage {
	
	private static class FilePathSorter implements Comparator<ApplicationFile> {
		@Override
		public int compare(ApplicationFile first, ApplicationFile second) {
			return first.phpFile.getAbsolutePath().compareTo(second.phpFile.getAbsolutePath());
		}
	}

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
			List<ApplicationFile> files = routineLineMap.analyzeFiles(
					ScriptDataFilename.ROUTINE_CATALOG.requireFile(datasetDirectory), phpDirectory,
					ScriptDataFilename.CFG.requireFile(datasetDirectory));
			
			Collections.sort(files, new FilePathSorter());
			
			String indent = "            ";
			int anonymousLineTotal = 0, adminLineTotal = 0, index;
			StringBuilder lineNumberList = new StringBuilder();
			for (ApplicationFile file : files) {
				index = 1;
				lineNumberList.setLength(0);
				lineNumberList.append(indent);

				int anonymousLineCount = 0, adminLineCount = 0;
				for (Integer userLevel : file.userLevelCoverage) {
					if (userLevel < 2) {
						anonymousLineCount++;
						
						lineNumberList.append(index);
						lineNumberList.append(", ");
						if (anonymousLineCount % 10 == 0) {
							lineNumberList.append("\n");
							lineNumberList.append(indent);
						}
					} else if (userLevel < Integer.MAX_VALUE) {
						adminLineCount++;
					}
					index++;
				}
				anonymousLineTotal += anonymousLineCount;
				adminLineTotal += adminLineCount + anonymousLineCount;
				
				Log.log("%05d %05d %s", anonymousLineCount, adminLineCount, file.phpFile.getAbsolutePath());
				Log.log(lineNumberList.toString());
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
