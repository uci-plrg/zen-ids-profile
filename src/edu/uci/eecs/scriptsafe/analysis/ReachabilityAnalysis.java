package edu.uci.eecs.scriptsafe.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;

public class ReachabilityAnalysis {

	private class EdgeSet {
		int entryPointHack;
		final Map<Integer, List<Integer>> edges = new HashMap<Integer, List<Integer>>();
		final Set<Integer> unreachedNodes = new HashSet<Integer>();
		final Set<Integer> localEntry = new HashSet<Integer>(); /* nodes having some incoming edge */

		final LinkedList<Integer> queue = new LinkedList<Integer>();

		void reset() {
			edges.clear();
			unreachedNodes.clear();
			localEntry.clear();
			queue.clear();
		}

		void addEdge(int from, int to) {
			List<Integer> source = edges.get(from);
			if (source == null) {
				source = new ArrayList<Integer>();
				edges.put(from, source);
			}
			source.add(to);
			unreachedNodes.add(from);
			unreachedNodes.add(to);
			localEntry.add(to);
		}

		void checkReachability(int requestId, String role) {
			// List<Integer> e = edges.get(ENTRY_POINT_HASH);
			queue.add(entryPointHack);
			unreachedNodes.remove(entryPointHack);
			queue.add(ENTRY_POINT_HASH);
			unreachedNodes.remove(ENTRY_POINT_HASH);
			Integer node;
			List<Integer> neighbors;
			do {
				node = queue.removeLast();
				neighbors = edges.get(node);
				if (neighbors == null)
					continue;
				for (Integer neighbor : neighbors) {
					if (unreachedNodes.remove(neighbor))
						queue.addFirst(neighbor);
				}
			} while (!queue.isEmpty());

			if (unreachedNodes.isEmpty()) {
				Log.log("Request #%d is fully reachable for %s", requestId, role);
			} else {
				Log.log("Request #%d has %d unreachable nodes for %s:", requestId, unreachedNodes.size(), role);
				for (Integer unreachedNode : unreachedNodes)
					Log.log("\t0x%x %s", unreachedNode, localEntry.contains(unreachedNode) ? "" : "(disconnected)");
			}
		}
	}

	public static final OptionArgumentMap.StringOption datasetDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private static final int ENTRY_POINT_HASH = 1;
	private static final int REQUEST_HEADER_TAG = 2;

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private File datasetDirectory;

	private ReachabilityAnalysis(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, datasetDir, verbose, watchlistFile, watchlistCategories);
	}

	private void run() {
		try {
			ScriptNode.init();

			argMap.parseOptions();

			Log.addOutput(System.out);
			Log.setLevel(Log.Level.values()[verbose.getValue()]);
			System.out.println("Log level " + verbose.getValue());

			if (!datasetDir.hasValue()) {
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
			File requestFile = new File(datasetDirectory, "request-edge.run");
			LittleEndianInputStream in = new LittleEndianInputStream(requestFile);
			int fromIndex, toRoutineHash, userLevel, requestId = -1;

			boolean isNewRequest = false;

			EdgeSet anonymousEdges = new EdgeSet();
			EdgeSet adminEdges = new EdgeSet();
			try {
				int firstField;
				while (in.ready(0x10)) {
					firstField = in.readInt();
					if (firstField == REQUEST_HEADER_TAG) {
						anonymousEdges.checkReachability(requestId, "anonymous");
						adminEdges.checkReachability(requestId, "admin");

						requestId = in.readInt();
						in.readInt();
						in.readInt();
						isNewRequest = true;
						continue;
					}
					fromIndex = in.readInt();
					userLevel = (fromIndex >>> 26);
					toRoutineHash = in.readInt();

					if (isNewRequest) {
						isNewRequest = false;
						adminEdges.entryPointHack = firstField;
						if (userLevel < 2)
							anonymousEdges.entryPointHack = firstField;
					}

					adminEdges.addEdge(firstField, toRoutineHash);
					if (userLevel < 2)
						anonymousEdges.addEdge(firstField, toRoutineHash);

					in.readInt();
				}
				anonymousEdges.checkReachability(requestId, "anonymous");
				adminEdges.checkReachability(requestId, "admin");

			} catch (Exception e) {
				Log.error("Failed to load file %s (skipping it):", requestFile.getAbsolutePath());
				Log.log(e);
			} finally {
				in.close();
			}

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -d <dataset-dir>", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		ReachabilityAnalysis analysis = new ReachabilityAnalysis(stack);
		analysis.run();
	}
}
