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
import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;

public class ReachabilityAnalysis {

	private class EdgeSet {
		final Map<Integer, List<Integer>> edges = new HashMap<Integer, List<Integer>>();
		final Set<Integer> unauthorizedTargets = new HashSet<Integer>();
		final Set<Integer> unreachedNodes = new HashSet<Integer>();
		final Set<Integer> localEntry = new HashSet<Integer>(); /* nodes having some incoming edge */

		final int maxUserLevel;
		final LinkedList<Integer> queue = new LinkedList<Integer>();

		EdgeSet(int maxUserLevel) {
			this.maxUserLevel = maxUserLevel;
		}

		void initialize() {
			queue.clear();

			edges.clear();
			unauthorizedTargets.clear();
			unreachedNodes.clear();
			localEntry.clear();
		}

		void addEdge(int from, int to, int userLevel) {
			if (userLevel > maxUserLevel) {
				unauthorizedTargets.add(to);
				// Log.log("\t0x%x -> 0x%x (unauthorized edge)", from, to);
			} else {
				localEntry.add(to);
				unreachedNodes.add(from);
				unreachedNodes.add(to);
				List<Integer> targets = getTargetList(edges, from);
				targets.add(to);
			}
		}

		void checkReachability(int requestId, String role) {
			if (unreachedNodes.isEmpty())
				return;

			checkReachability(ENTRY_POINT_HASH);

			if (unreachedNodes.isEmpty()) {
				Log.log("Request #%d is fully reachable for %s", requestId, role);
			} else {
				Integer subgraphEntry;
				do {
					subgraphEntry = null;
					for (Integer unreachedNode : unreachedNodes) {
						if (unauthorizedTargets.contains(unreachedNode) && !localEntry.contains(unreachedNode)) {
							Log.log("\t0x%x (user level re-entry)", unreachedNode);
							subgraphEntry = unreachedNode;
							break;
						}
					}
					if (subgraphEntry != null)
						checkReachability(subgraphEntry);
				} while (subgraphEntry != null);

				Log.log("Request #%d has %d unreachable nodes for %s:", requestId, unreachedNodes.size(), role);
				for (Integer unreachedNode : unreachedNodes) {
					if (unauthorizedTargets.contains(unreachedNode)) {
						if (localEntry.contains(unreachedNode))
							Log.log("\t0x%x", unreachedNode);
					} else {
						Log.log("\t0x%x %s", unreachedNode, localEntry.contains(unreachedNode) ? "" : "(disconnected)");
					}
				}
			}
		}

		void checkReachability(int entryPoint) {
			queue.add(entryPoint);
			unreachedNodes.remove(entryPoint);
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
		}

		private List<Integer> getTargetList(Map<Integer, List<Integer>> edgeSet, Integer source) {
			List<Integer> targets = edgeSet.get(source);
			if (targets == null) {
				targets = new ArrayList<Integer>();
				edgeSet.put(source, targets);
			}
			return targets;
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
			File requestFile = ScriptDataFilename.REQUEST_GRAPH.requireFile(datasetDirectory);
			LittleEndianInputStream in = new LittleEndianInputStream(requestFile);
			int fromIndex, toRoutineHash, userLevel, requestId = -1;

			EdgeSet anonymousEdges = new EdgeSet(1);
			EdgeSet adminEdges = new EdgeSet(Integer.MAX_VALUE);
			try {
				int firstField;
				while (in.ready(0x10)) {
					firstField = in.readInt();
					if (firstField == REQUEST_HEADER_TAG) {
						anonymousEdges.checkReachability(requestId, "anonymous");
						adminEdges.checkReachability(requestId, "admin");
						anonymousEdges.initialize();
						adminEdges.initialize();

						requestId = in.readInt();
						in.readInt();
						in.readInt();
						continue;
					}
					fromIndex = in.readInt();
					userLevel = (fromIndex >>> 26);
					toRoutineHash = in.readInt();

					adminEdges.addEdge(firstField, toRoutineHash, userLevel);
					anonymousEdges.addEdge(firstField, toRoutineHash, userLevel);

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
