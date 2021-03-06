package edu.uci.plrg.cfi.php.analysis.dictionary;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.ArgumentStack;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap.OptionMode;
import edu.uci.plrg.cfi.php.merge.ScriptMergeWatchList;
import edu.uci.plrg.cfi.php.merge.graph.ScriptDataFilename;
import edu.uci.plrg.cfi.php.merge.graph.ScriptFlowGraph;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode;

public class DictionaryGenerator {

	public static final OptionArgumentMap.StringOption port = OptionArgumentMap.createStringOption('p');
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

	private DictionaryGenerator(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, port, datasetDir, phpDir, verbose, watchlistFile, watchlistCategories);
	}

	private void run() {
		ScriptNode.init();

		argMap.parseOptions();

		Log.addOutput(System.out);
		Log.setLevel(Log.Level.values()[verbose.getValue()]);
		System.out.println("Log level " + verbose.getValue());

		try {
			if (!(port.hasValue() && datasetDir.hasValue() && phpDir.hasValue())) {
				printUsage();
				return;
			}

			serverPort = Integer.parseInt(port.getValue());
			if (serverPort < 0 || serverPort > Short.MAX_VALUE) {
				Log.error("Port %d does not exist. Exiting now.", serverPort);
				return;
			}

			File datasetDirectory = new File(datasetDir.getValue());
			File phpDirectory = new File(phpDir.getValue());
			routineLineMap.load(ScriptDataFilename.ROUTINE_CATALOG.requireFile(datasetDirectory), phpDirectory,
					ScriptDataFilename.CFG.requireFile(datasetDirectory));

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}

			// Log.log("%s", routineLineMap.toString());

			startServer();

		} catch (Throwable t) {
			Log.error("Uncaught %s exception:", t.getClass().getSimpleName());
			Log.log(t);
		}
	}

	private void startServer() throws IOException {
		ServerSocket server = new ServerSocket(serverPort);
		DictionaryRequestHandler requestHandler = new DictionaryRequestHandler(routineLineMap);
		Socket request;

		try {
			while (true) {
				request = server.accept();
				requestHandler.respond(request);
			}
		} finally {
			server.close();
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -p <server-port> -d <dataset-dir> -s <php-src-dir>", getClass()
				.getSimpleName()));
	}

	public static void main(String[] args) {
		try {
			ArgumentStack stack = new ArgumentStack(args);
			DictionaryGenerator exporter = new DictionaryGenerator(stack);
			exporter.run();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
