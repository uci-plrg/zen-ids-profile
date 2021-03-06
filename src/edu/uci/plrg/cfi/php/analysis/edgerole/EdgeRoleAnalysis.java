package edu.uci.plrg.cfi.php.analysis.edgerole;

import java.io.File;
import java.nio.file.Path;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.ArgumentStack;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap.OptionMode;
import edu.uci.plrg.cfi.php.analysis.AnalysisException;
import edu.uci.plrg.cfi.php.merge.ScriptMergeWatchList;
import edu.uci.plrg.cfi.php.merge.graph.RoutineEdge;
import edu.uci.plrg.cfi.php.merge.graph.RoutineId;
import edu.uci.plrg.cfi.php.merge.graph.ScriptDataFilename;
import edu.uci.plrg.cfi.php.merge.graph.ScriptFlowGraph;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;
import edu.uci.plrg.cfi.php.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.plrg.cfi.php.merge.graph.loader.ScriptGraphDataFiles.Type;

public class EdgeRoleAnalysis {
	public static final OptionArgumentMap.StringOption phpDir = OptionArgumentMap.createStringOption('s');
	public static final OptionArgumentMap.StringOption datasetDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final ScriptDatasetLoader datasetLoader = new ScriptDatasetLoader();
	private ScriptFlowGraph dataset;

	private File datasetDirectory;

	// private final RequestGraph.Loader requestLoader = new RequestGraph.Loader();

	private EdgeRoleAnalysis(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, phpDir, datasetDir, verbose, watchlistFile, watchlistCategories);
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

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}

			// requestLoader.addPath(new File(datasetDir.getValue()).toPath());
			// RequestGraph graph = requestLoader.load();

			datasetDirectory = new File(datasetDir.getValue());
			File datasetFile = ScriptDataFilename.CFG.requireFile(datasetDirectory);
			File routineCatalogFile = ScriptDataFilename.ROUTINE_CATALOG.requireFile(datasetDirectory);
			dataset = new ScriptFlowGraph(Type.DATASET, datasetFile.getAbsolutePath(), false);
			datasetLoader.loadDataset(datasetFile, routineCatalogFile, dataset, false);

			for (Path file : RoutineId.Cache.INSTANCE.getAllKnownFiles()) {
				Log.log(" === %s", file);
				for (int routineHash : RoutineId.Cache.INSTANCE.getRoutinesInFile(file)) {
					int adminEdges = 0, anonymousEdges = 0;
					ScriptRoutineGraph routine = dataset.getRoutine(routineHash);
					for (RoutineEdge edge : dataset.edges.getIncomingEdges(routineHash)) {
						if (edge.getUserLevel() < 2)
							anonymousEdges++;
						else
							adminEdges++;
					}
					if (adminEdges > 0 || anonymousEdges > 0)
						Log.log("\t%03d/%03d 0x%x %s", adminEdges, anonymousEdges, routine.hash, routine.id.name);
					else
						Log.log("\t---/--- 0x%x %s", routine.hash, routine.id.name);
				}
			}

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -d <dataset-dir> -s <php-src-dir>", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		EdgeRoleAnalysis analysis = new EdgeRoleAnalysis(stack);
		analysis.run();
	}
}
