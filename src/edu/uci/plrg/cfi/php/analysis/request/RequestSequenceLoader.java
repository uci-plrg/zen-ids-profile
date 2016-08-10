package edu.uci.plrg.cfi.php.analysis.request;

import java.io.File;
import java.io.IOException;

import edu.uci.plrg.cfi.common.io.LittleEndianInputStream;
import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.php.merge.graph.ScriptFlowGraph;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;
import edu.uci.plrg.cfi.php.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.plrg.cfi.php.merge.graph.loader.ScriptGraphDataFiles.Type;
import edu.uci.plrg.cfi.php.merge.graph.loader.ScriptNodeLoader;

public class RequestSequenceLoader {

	public interface RequestCollection extends ScriptNodeLoader.LoadContext {
		boolean startRequest(int requestId, File routineCatalog);

		boolean addEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, int toIndex, int userLevel,
				File routineCatalog) throws NumberFormatException, IOException;

		public void addRoutine(ScriptRoutineGraph routine);
	}

	private static final int REQUEST_HEADER_TAG = 2;

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
		LittleEndianInputStream in = new LittleEndianInputStream(fileSet.requestFile);
		int firstField, requestId, fromIndex, toRoutineHash, toIndex, userLevel, totalRequests = 0;

		try {
			while (in.ready(0x10)) {
				firstField = in.readInt();
				if (firstField == REQUEST_HEADER_TAG) {
					requestId = in.readInt();
					in.readInt();
					in.readInt();
					totalRequests++;
					if (requests.startRequest(requestId, fileSet.routineCatalog))
						continue;
					else
						break;
				}
				fromIndex = in.readInt();
				userLevel = (fromIndex >>> 26);
				fromIndex = (fromIndex & 0x3ffffff);
				toRoutineHash = in.readInt();
				toIndex = in.readInt();

				if (!requests.addEdge(firstField, fromIndex, toRoutineHash, toIndex, userLevel, fileSet.routineCatalog))
					break;
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
