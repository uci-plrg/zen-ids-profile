package edu.uci.eecs.scriptsafe.analysis.dictionary;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.analysis.AnalysisException;
import edu.uci.eecs.scriptsafe.analysis.dictionary.DictionaryRequestHandler.Instruction;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;

public class DictionaryTest {
	public static final OptionArgumentMap.StringOption port = OptionArgumentMap.createStringOption('p');
	public static final OptionArgumentMap.StringOption datasetDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final RoutineLineMap routineLineMap = new RoutineLineMap();

	private final ScriptDatasetLoader datasetLoader = new ScriptDatasetLoader();
	private ScriptFlowGraph dataset;

	private File outputFile;

	private int serverPort;
	private Socket socket;
	private OutputStream out;

	private DictionaryTest(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, port, datasetDir, verbose, watchlistFile, watchlistCategories);
	}

	private void run() {
		try {
			ScriptNode.init();

			argMap.parseOptions();

			Log.addOutput(System.out);
			Log.setLevel(Log.Level.values()[verbose.getValue()]);
			System.out.println("Log level " + verbose.getValue());

			if (!(port.hasValue() && datasetDir.hasValue())) {
				printUsage();
				return;
			}

			serverPort = Integer.parseInt(port.getValue());
			if (serverPort < 0 || serverPort > Short.MAX_VALUE) {
				Log.error("Port %d does not exist. Exiting now.", serverPort);
				return;
			}

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}

			File datasetDirectory = new File(datasetDir.getValue());
			File datasetFile = new File(datasetDirectory, "cfg.set");
			if (!(datasetFile.exists() && datasetFile.isFile()))
				throw new AnalysisException("Cannot find dataset file '%s'", datasetFile.getAbsolutePath());
			dataset = new ScriptFlowGraph(Type.DATASET, datasetFile.getAbsolutePath(), false);
			datasetLoader.loadDataset(datasetFile, dataset);

			List<ScriptRoutineGraph> trainingRoutines = new ArrayList<ScriptRoutineGraph>();
			List<ScriptRoutineGraph> testRoutines = new ArrayList<ScriptRoutineGraph>();
			Random random = new Random(System.currentTimeMillis());
			for (ScriptRoutineGraph routine : dataset.getRoutines()) {
				if (random.nextInt() % 1000 < 700)
					trainingRoutines.add(routine);
				else
					testRoutines.add(routine);
			}

			socket = new Socket(InetAddress.getLocalHost(), serverPort);
			out = socket.getOutputStream();
			try {
				sendInstruction(Instruction.RESET);
				for (ScriptRoutineGraph routine : trainingRoutines) {
					sendInstruction(Instruction.GET_ADMIN_PROBABILITY, routine.hash);
					sendInstruction(Instruction.ADD_ROUTINE, routine.hash);
				}
				for (ScriptRoutineGraph routine : testRoutines) {
					sendInstruction(Instruction.GET_ADMIN_PROBABILITY, routine.hash,
							dataset.edges.getMinUserLevel(routine.hash) >= 2);
				}
				// sendInstruction(Instruction.REPORT_SUMMARY);
			} finally {
				out.close();
				socket.close();
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void sendInstruction(Instruction i) throws IOException {
		sendInstruction(i, 0);
	}

	private void sendInstruction(Instruction i, int hash) throws IOException {
		byte instruction[] = Instruction.create(i, hash);
		out.write(instruction);
		out.flush();
	}

	private void sendInstruction(Instruction i, int hash, boolean isAdmin) throws IOException {
		byte instruction[] = Instruction.create(i, hash);
		instruction[0] |= (isAdmin ? 0x80 : 0x40);
		out.write(instruction);
		out.flush();
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -p <server-port> -d <dataset-dir>", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		DictionaryTest test = new DictionaryTest(stack);
		test.run();
	}

}
