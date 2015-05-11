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
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;

public class FeatureService {

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

	private int serverPort;

	private FeatureDataSource dataSource;
	private final EdgeFeatureCollector edgeCollector = new EdgeFeatureCollector();
	private final ByteBuffer reader = ByteBuffer.allocate(FeatureOperation.OPERATION_BYTE_COUNT);

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

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}

			dataSource = new FeatureDataSource(datasetDir.getValue(), phpDir.getValue());
			edgeCollector.setDataSource(dataSource);

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
				response = edgeCollector.getFeatures(fromRoutineHash, fromOpcode, toRoutineHash);
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
