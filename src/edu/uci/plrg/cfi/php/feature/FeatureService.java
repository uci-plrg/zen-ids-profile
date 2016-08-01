package edu.uci.plrg.cfi.php.feature;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Properties;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.ArgumentStack;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap.OptionMode;
import edu.uci.plrg.cfi.php.analysis.request.RequestEdgeSummary;
import edu.uci.plrg.cfi.php.merge.ScriptMergeWatchList;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode;

public class FeatureService {

	public static final OptionArgumentMap.StringOption port = OptionArgumentMap.createStringOption('p');
	public static final OptionArgumentMap.StringOption datasetDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.StringOption phpDir = OptionArgumentMap.createStringOption('s');
	public static final OptionArgumentMap.StringOption crossValidationFilePath = OptionArgumentMap
			.createStringOption('k');
	public static final OptionArgumentMap.StringOption configFilePath = OptionArgumentMap.createStringOption('i');
	public static final OptionArgumentMap.BooleanOption testOption = OptionArgumentMap.createBooleanOption('t', false);
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
	private EdgeFeatureCollector edgeCollector = new EdgeFeatureCollector();
	private GraphFeatureCollector graphCollector = new GraphFeatureCollector();

	private FeatureService(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, port, datasetDir, phpDir, crossValidationFilePath, configFilePath,
				testOption, verbose, watchlistFile, watchlistCategories);
	}

	private void start() {
		ScriptNode.init();

		argMap.parseOptions();

		Log.addOutput(System.out);
		Log.setLevel(Log.Level.values()[verbose.getValue()]);
		System.out.println("Log level " + verbose.getValue());

		try {
			if (!((testOption.getValue() || port.hasValue()) && datasetDir.hasValue() && phpDir.hasValue()
					&& crossValidationFilePath.hasValue() && configFilePath.hasValue())) {
				printUsage();
				return;
			}

			if (port.hasValue()) {
				serverPort = Integer.parseInt(port.getValue());
				if (serverPort < 0 || serverPort > Short.MAX_VALUE) {
					Log.error("Port %d does not exist. Exiting now.", serverPort);
					return;
				}
			}

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}

			Properties config = new Properties();
			config.load(new FileInputStream(new File(configFilePath.getValue())));
			FeatureCrossValidationSets crossValidationSets = new FeatureCrossValidationSets(new File(
					crossValidationFilePath.getValue()));
			dataSource = new FeatureDataSource(datasetDir.getValue(), phpDir.getValue(), config, crossValidationSets);

			edgeCollector.setDataSource(dataSource);
			graphCollector.setDataSource(dataSource);
			
			if (testOption.hasValue())
				testServer();
			else
				listen();

		} catch (Throwable t) {
			Log.error("Uncaught %s exception:", t.getClass().getSimpleName());
			Log.log(t);
		}
	}

	void testServer() {
		FeatureServerTest test = new FeatureServerTest(this);
		test.run();
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
		ByteBuffer reader = ByteBuffer.allocate(FeatureOperation.OPERATION_BYTE_COUNT);
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

	ByteBuffer execute(ByteBuffer data) throws IOException {
		ByteBuffer response;
		FeatureOperation op = FeatureOperation.forByte(data.get());
		int field1 = data.getInt();
		int field2 = data.getShort();
		int field3 = data.getInt();

		try {
			switch (op) {
				case TRAIN_ON_K:
					dataSource.trainingRequestGraph.train(field1);
					dataSource.wordList.reload();
					response = FeatureResponse.OK.generateResponse();
					break;
				case GET_K_DELTA:
					response = dataSource.trainingRequestGraph.getDelta(field1);
					break;
				case GET_FEATURES:
					response = edgeCollector.getFeatures(field1, field2, field3);
					break;
				case GET_GRAPH_PROPERTIES:
					response = graphCollector.getFeatures();
					break;
				default:
					response = FeatureResponse.ERROR.generateResponse();
					break;
			}
		} catch (Exception e) {
			response = FeatureResponse.ERROR.generateResponse();
			Log.error("Failed to handle request %s", op);
			Log.log(e);
		}
		response.rewind();
		return response;
	}

	FeatureDataSource getDataSource() {
		return dataSource;
	}

	private void printUsage() {
		System.err.println("Usage for service:");
		System.err.println("  -p <server-port>");
		System.err.println("  -d <dataset-dir>");
		System.err.println("  -s <php-src-dir>");
		System.err.println("  -k <cross-validation-file>");
		System.err.println("  -i <config-file>");
		System.err.println("Usage for test:");
		System.err.println("  -t (test)");
		System.err.println("  -d <dataset-dir>");
		System.err.println("  -s <php-src-dir>");
		System.err.println("  -k <cross-validation-file>");
		System.err.println("  -i <config-file>");
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
