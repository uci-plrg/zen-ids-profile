package edu.uci.eecs.scriptsafe.analysis.request;

import java.io.File;
import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptNodeLoader;

public class RequestSequenceLoader {

	public interface RequestCollection extends ScriptNodeLoader.LoadContext {
		void startRequest(int requestId, File routineCatalog);

		void addEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, int userLevel, File routineCatalog)
				throws NumberFormatException, IOException;

		public void addRoutine(ScriptRoutineGraph routine);
	}

	private static final int REQUEST_HEADER_TAG = 2;
	
	private static final ScriptNodeLoader nodeLoader = new ScriptNodeLoader();
	private static final ScriptDatasetLoader cfgLoader = new ScriptDatasetLoader();
	
	public static int peekRequestCount(File requestFile) throws IOException {
		LittleEndianInputStream in = new LittleEndianInputStream(requestFile);
		int firstField, totalRequests = 0;

		try {
			while (in.ready(0x10)) {
				firstField = in.readInt();
				in.readInt();
				in.readInt();
				in.readInt();
				if (firstField == REQUEST_HEADER_TAG)
					totalRequests++;
			}
		} catch (Exception e) {
			Log.error("Failed to load request count from file %s:", requestFile.getAbsolutePath());
			Log.log(e);
		} finally {
			in.close();
		}
		return totalRequests;
	}

	public static int load(RequestFileSet fileSet, RequestCollection requests) throws IOException {
		nodeLoader.setLoadContext(requests);
		if (fileSet.nodeFile != null) {
			nodeLoader.loadNodes(fileSet.nodeFile);
		} else {
			ScriptFlowGraph cfg = new ScriptFlowGraph(Type.DATASET, fileSet.datasetFile.getAbsolutePath(), false);
			cfgLoader.loadDataset(fileSet.datasetFile, fileSet.routineCatalog, cfg);
			for (ScriptRoutineGraph routine : cfg.getRoutines())
				requests.addRoutine(routine);
		}

		LittleEndianInputStream in = new LittleEndianInputStream(fileSet.requestFile);
		int firstField, requestId, fromIndex, toRoutineHash, userLevel, totalRequests = 0;

		try {
			while (in.ready(0x10)) {
				firstField = in.readInt();
				if (firstField == REQUEST_HEADER_TAG) {
					requestId = in.readInt();
					in.readInt();
					in.readInt();
					totalRequests++;
					requests.startRequest(requestId, fileSet.routineCatalog);
					continue;
				}
				fromIndex = in.readInt();
				userLevel = (fromIndex >>> 26);
				fromIndex = (fromIndex & 0x3ffffff);
				toRoutineHash = in.readInt();

				requests.addEdge(firstField, fromIndex, toRoutineHash, userLevel, fileSet.routineCatalog);

				in.readInt();
			}
		} catch (Exception e) {
			Log.error("Failed to load file %s (skipping it):", fileSet.requestFile.getAbsolutePath());
			Log.log(e);
		} finally {
			in.close();
		}
		return totalRequests;
	}

}
