package edu.uci.plrg.cfi.php.analysis.dictionary;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.ArgumentStack;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap.OptionMode;
import edu.uci.plrg.cfi.php.analysis.AnalysisException;
import edu.uci.plrg.cfi.php.analysis.dictionary.DictionaryRequestHandler.Instruction;
import edu.uci.plrg.cfi.php.merge.ScriptMergeWatchList;
import edu.uci.plrg.cfi.php.merge.graph.RoutineEdge;
import edu.uci.plrg.cfi.php.merge.graph.ScriptDataFilename;
import edu.uci.plrg.cfi.php.merge.graph.ScriptFlowGraph;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;
import edu.uci.plrg.cfi.php.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.plrg.cfi.php.merge.graph.loader.ScriptGraphDataFiles.Type;

public class DictionaryTest {

	private class TestSelector {
		final Random random = new Random(System.currentTimeMillis());

		boolean useForTraining(ScriptRoutineGraph routine) {
			if (trainTest.getValue())
				return random.nextInt(1000) > 700;
			else
				return true;
		}
	}

	public static final OptionArgumentMap.BooleanOption standaloneMode = OptionArgumentMap.createBooleanOption('1');
	public static final OptionArgumentMap.StringOption phpDir = OptionArgumentMap.createStringOption('s');
	public static final OptionArgumentMap.StringOption port = OptionArgumentMap.createStringOption('p');
	public static final OptionArgumentMap.StringOption datasetDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.IntegerOption predictorCount = OptionArgumentMap.createIntegerOption('n', 0);
	public static final OptionArgumentMap.BooleanOption trainTest = OptionArgumentMap.createBooleanOption('t', false);
	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final RoutineLineMap routineLineMap = new RoutineLineMap();
	private final TestSelector testSelector = new TestSelector();

	private final ScriptDatasetLoader datasetLoader = new ScriptDatasetLoader();
	private ScriptFlowGraph dataset;

	private File outputFile;

	private int serverPort;
	private Socket socket;
	private OutputStream out;

	private File datasetDirectory;

	int adminRoutineCount = 0, anonymousRoutineCount = 0;
	private List<ScriptRoutineGraph> trainingRoutines = new ArrayList<ScriptRoutineGraph>();
	private List<ScriptRoutineGraph> testRoutines = new ArrayList<ScriptRoutineGraph>();

	private DictionaryTest(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, standaloneMode, phpDir, port, datasetDir, predictorCount, trainTest,
				verbose, watchlistFile, watchlistCategories);
	}

	private void run() {
		try {
			ScriptNode.init();

			argMap.parseOptions();

			Log.addOutput(System.out);
			Log.setLevel(Log.Level.values()[verbose.getValue()]);
			System.out.println("Log level " + verbose.getValue());

			boolean valid = datasetDir.hasValue();
			if (standaloneMode.hasValue())
				valid |= phpDir.hasValue();
			else
				valid |= port.hasValue();

			if (!valid) {
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

			datasetDirectory = new File(datasetDir.getValue());
			File datasetFile = ScriptDataFilename.CFG.requireFile(datasetDirectory);
			File routineCatalogFile = ScriptDataFilename.ROUTINE_CATALOG.requireFile(datasetDirectory);
			dataset = new ScriptFlowGraph(Type.DATASET, datasetFile.getAbsolutePath(), false);
			datasetLoader.loadDataset(datasetFile, routineCatalogFile, dataset, false);

			for (ScriptRoutineGraph routine : dataset.getRoutines()) {
				if (dataset.edges.getIncomingEdgeCount(routine.hash) == 0)
					continue;

				if (testSelector.useForTraining(routine))
					trainingRoutines.add(routine);
				else
					testRoutines.add(routine);

				if (dataset.edges.getMinUserLevel(routine.hash) < 2) {
					anonymousRoutineCount++;
				} else {
					adminRoutineCount++;
				}
			}

			if (standaloneMode.hasValue()) {
				testStandalone();
			} else {
				testServer();
			}

			Log.log("Total admin routines: %d. Total anonymous routines: %d.", adminRoutineCount, anonymousRoutineCount);

			int adminEdges = 0, anonymousEdges = 0;
			for (List<RoutineEdge> edges : dataset.edges.getOutgoingEdges()) {
				for (RoutineEdge edge : edges) {
					if (edge.getUserLevel() < 2)
						anonymousEdges++;
					else if (edge.getUserLevel() < 60)
						adminEdges++;
				}
			}
			Log.log("Total admin edges: %d. Total anonymous edges: %d.", adminEdges, anonymousEdges);

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void testStandalone() throws NumberFormatException, IOException {
		File phpDirectory = new File(phpDir.getValue());
		routineLineMap.load(ScriptDataFilename.ROUTINE_CATALOG.requireFile(datasetDirectory), phpDirectory,
				ScriptDataFilename.CFG.requireFile(datasetDirectory));
		DictionaryRequestHandler requestHandler = new DictionaryRequestHandler(routineLineMap);

		for (ScriptRoutineGraph routine : trainingRoutines) {
			requestHandler.execute(createInstruction(Instruction.ADD_ROUTINE, routine.hash,
					dataset.edges.getMinUserLevel(routine.hash) >= 2));
		}
		for (ScriptRoutineGraph routine : testRoutines) {
			requestHandler.execute(createInstruction(Instruction.GET_ADMIN_PROBABILITY, routine.hash,
					dataset.edges.getMinUserLevel(routine.hash) >= 2));
		}
		requestHandler.execute(createInstruction(Instruction.REPORT_SUMMARY, predictorCount.getValue()));
	}

	private void testServer() throws UnknownHostException, IOException {
		serverPort = Integer.parseInt(port.getValue());
		if (serverPort < 0 || serverPort > Short.MAX_VALUE) {
			Log.error("Port %d does not exist. Exiting now.", serverPort);
			return;
		}

		socket = new Socket(InetAddress.getLocalHost(), serverPort);
		out = socket.getOutputStream();
		try {
			sendInstruction(createInstruction(Instruction.RESET));
			for (ScriptRoutineGraph routine : trainingRoutines) {
				sendInstruction(createInstruction(Instruction.ADD_ROUTINE, routine.hash));
			}
			for (ScriptRoutineGraph routine : testRoutines) {
				sendInstruction(createInstruction(Instruction.GET_ADMIN_PROBABILITY, routine.hash,
						dataset.edges.getMinUserLevel(routine.hash) >= 2));
			}
			sendInstruction(createInstruction(Instruction.REPORT_SUMMARY, predictorCount.getValue()));
		} finally {
			out.close();
			socket.close();
		}
	}

	private void sendInstruction(byte instruction[]) throws IOException {
		out.write(instruction);
		out.flush();
	}

	private byte[] createInstruction(Instruction i) throws IOException {
		return createInstruction(i, 0);
	}

	private byte[] createInstruction(Instruction i, int hash) throws IOException {
		return Instruction.create(i, hash);
	}

	private byte[] createInstruction(Instruction i, int hash, boolean isAdmin) throws IOException {
		byte instruction[] = Instruction.create(i, hash);
		instruction[0] |= (isAdmin ? 0x80 : 0x40);
		return instruction;
	}

	private void printUsage() {
		System.err.println(String.format(
				"Usage: %s -d <dataset-dir> [ -1 <standalone-mode> -s <php-src-dir> ] [ -p <server-port> ]", getClass()
						.getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		DictionaryTest test = new DictionaryTest(stack);
		test.run();
	}
}
