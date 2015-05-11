package edu.uci.eecs.scriptsafe.feature;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.analysis.AnalysisException;
import edu.uci.eecs.scriptsafe.analysis.dictionary.Dictionary;
import edu.uci.eecs.scriptsafe.analysis.dictionary.RoutineLineMap;
import edu.uci.eecs.scriptsafe.analysis.dictionary.SkewDictionary;
import edu.uci.eecs.scriptsafe.analysis.request.RequestCallSiteSummary;
import edu.uci.eecs.scriptsafe.analysis.request.RequestEdgeSummary;
import edu.uci.eecs.scriptsafe.analysis.request.RequestGraph;
import edu.uci.eecs.scriptsafe.analysis.request.RequestGraphLoader;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;

public class FeatureService {

	private static class RoleCounts {
		int adminCount = 0;
		int anonymousCount = 0;

		void addCounts(RequestEdgeSummary edge) {
			if (edge != null) {
				adminCount += edge.getAdminCount();
				anonymousCount += edge.getAnonymousCount();
			}
		}

		void write(ByteBuffer buffer) {
			buffer.putInt(adminCount);
			buffer.putInt(anonymousCount);
		}
	}

	public static final OptionArgumentMap.StringOption port = OptionArgumentMap.createStringOption('p');
	public static final OptionArgumentMap.StringOption datasetDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.StringOption phpDir = OptionArgumentMap.createStringOption('s');
	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private static final ByteBuffer intBuffer = ByteBuffer.allocate(4);

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final RoutineLineMap routineLineMap = new RoutineLineMap();
	private final RequestGraphLoader requestLoader = new RequestGraphLoader();
	private RequestGraph requestGraph;
	private final ScriptDatasetLoader datasetLoader = new ScriptDatasetLoader();
	private ScriptFlowGraph dataset;

	private int serverPort;
	private Dictionary dictionary;

	private FeatureService(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, port, datasetDir, phpDir, verbose, watchlistFile, watchlistCategories);
	}

	private void start() {
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
			datasetLoader.loadDataset(datasetFile, routineCatalogFile, dataset);
			routineLineMap.load(routineCatalogFile, phpDirectory, datasetFile);
			dictionary = new SkewDictionary(routineLineMap);
			requestLoader.addPath(datasetDirectory.toPath());
			requestGraph = requestLoader.load();

			listen();

		} catch (Throwable t) {
			Log.error("Uncaught %s exception:", t.getClass().getSimpleName());
			Log.log(t);
		}
	}

	void listen() throws IOException {
		SocketChannel client;
		ServerSocketChannel listener = ServerSocketChannel.open();
		listener.bind(new InetSocketAddress(serverPort));
		while (true) {
			client = listener.accept();
			respond(client);
		}
	}

	private static final ByteBuffer reader = ByteBuffer.allocate(FeatureOperation.OPERATION_BYTE_COUNT);

	void respond(SocketChannel client) throws IOException {
		try {
			ByteBuffer response;
			client.configureBlocking(true);
			while (true) {
				reader.rewind();
				client.read(reader);
				reader.rewind();
				response = execute(reader);
				client.write(response);
			}
		} finally {
			client.close();
		}
	}

	ByteBuffer execute(ByteBuffer data) {
		ByteBuffer response;
		FeatureOperation op = FeatureOperation.forByte(reader.get());
		int fromRoutineHash = reader.getInt();
		int fromOpcode = reader.getShort();
		int toRoutineHash = reader.getInt();

		switch (op) {
			case GET_FEATURES:
				response = getFeatures(fromRoutineHash, fromOpcode, toRoutineHash);
				break;
			case GET_EDGE_LABEL:
				response = getEdgeLabel(fromRoutineHash, fromOpcode, toRoutineHash);
				break;
			case GET_GRAPH_PROPERTIES:
				response = getGraphProperties();
				break;
			default:
				response = ByteBuffer.allocate(1).put((byte) 0);
				break;
		}
		return response;
	}

	// feature data: { site <role-counts>, calling sites <role-counts>, target <role-counts>,
	// target file <role-counts>, target directory <role-counts>,
	// <role-counts>*, word* }
	private ByteBuffer getFeatures(int fromRoutineHash, int fromOpcode, int toRoutineHash) {
		RoleCounts callSiteCounts = new RoleCounts();
		RequestCallSiteSummary callSite = requestGraph.getCallSite(fromRoutineHash, fromOpcode);
		for (RequestEdgeSummary edge : callSite.getEdges())
			callSiteCounts.addCounts(edge);

		RoleCounts callingSiteCounts = new RoleCounts();
		for (RoutineEdge edge : dataset.edges.getIncomingEdges(toRoutineHash)) {
			callingSiteCounts.addCounts(requestGraph.getEdge(edge.getFromRoutineHash(), edge.getFromRoutineIndex(),
					toRoutineHash));
		}

		ByteBuffer response = ByteBuffer.allocate(999);
		callSiteCounts.write(response);
		callingSiteCounts.write(response);
		return response;
	}

	private ByteBuffer getEdgeLabel(int fromRoutineHash, int fromOpcode, int toRoutineHash) {
		return null;
	}

	private ByteBuffer getGraphProperties() {
		return null;
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -p <server-port> -d <dataset-dir> -s <php-src-dir>", getClass()
				.getSimpleName()));
	}

	public static void main(String args[]) {
		try {
			ArgumentStack stack = new ArgumentStack(args);
			FeatureService service = new FeatureService(stack);
			service.start();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
