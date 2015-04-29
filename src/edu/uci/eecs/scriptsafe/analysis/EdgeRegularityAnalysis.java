package edu.uci.eecs.scriptsafe.analysis;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptNodeLoader;

public class EdgeRegularityAnalysis {

	private static class CallSiteKey {
		final int routineHash;
		final int nodeIndex;

		CallSiteKey(int routineHash, int nodeIndex) {
			this.routineHash = routineHash;
			this.nodeIndex = nodeIndex;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + nodeIndex;
			result = prime * result + routineHash;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CallSiteKey other = (CallSiteKey) obj;
			if (nodeIndex != other.nodeIndex)
				return false;
			if (routineHash != other.routineHash)
				return false;
			return true;
		}
	}

	private class CallSite {
		final ScriptRoutineGraph routine;
		final ScriptNode node;
		final List<Edge> edges = new ArrayList<Edge>();

		double regularity = 0.0;

		CallSite(ScriptRoutineGraph routine, ScriptNode node) {
			this.routine = routine;
			this.node = node;
		}

		void addEdge(int toRoutineHash, int userLevel) {
			ScriptRoutineGraph callee = routines.get(toRoutineHash);
			boolean found = false;
			for (Edge edge : edges) {
				if (edge.matches(callee, userLevel)) {
					edge.count++;
					found = true;
				}
			}
			if (!found)
				edges.add(new Edge(this, callee, userLevel));
		}

		void calculateRegularity() {
			if (edges.size() == 1) {
				regularity = 1.0;
				return;
			}

			int totalCalls = 0, maxCalls = 0, minCalls = Integer.MAX_VALUE;
			for (Edge edge : edges) {
				totalCalls += edge.count;
				if (edge.count > maxCalls)
					maxCalls = edge.count;
				if (edge.count < minCalls)
					minCalls = edge.count;
			}
			regularity = (minCalls / (double) maxCalls);
			if (regularity < 0.5) {
				double normalizer = 1 + (.5 / (double) Math.min(1, totalCalls / 10));
				regularity *= normalizer;
			}
		}
	}

	private class Edge {
		final CallSite callSite;
		final int userLevel;
		final ScriptRoutineGraph callee;

		int count = 1;

		Edge(CallSite callSite, ScriptRoutineGraph callee, int userLevel) {
			this.callSite = callSite;
			this.userLevel = userLevel;
			this.callee = callee;
		}

		boolean matches(ScriptRoutineGraph callee, int userLevel) {
			return this.callee.hash == callee.hash && this.userLevel == userLevel;
		}
	}

	private class RequestSet {
		private final File requestFile;
		private final File nodeFile;

		RequestSet(File requestFile, File nodeFile) {
			this.requestFile = requestFile;
			this.nodeFile = nodeFile;
		}

		int load() throws IOException {
			nodeLoader.loadNodes(nodeFile);

			LittleEndianInputStream in = new LittleEndianInputStream(requestFile);
			int fromIndex, toRoutineHash, userLevel, totalRequests = 0;
			CallSite callSite;

			// fwrite(&request_header_tag, sizeof(uint), 1, cfg_files->request_edge);
			// fwrite(&request_state.request_id, sizeof(uint), 1, cfg_files->request_edge);
			// fwrite(&session.hash, sizeof(uint), 1, cfg_files->request_edge);
			// fwrite(&timestamp, sizeof(uint), 1, cfg_files->request_edge);

			// fwrite(&from_routine_hash, sizeof(uint), 1, cfg_files->request_edge);
			// fwrite(&packed_from_index, sizeof(uint), 1, cfg_files->request_edge);
			// fwrite(&to_routine_hash, sizeof(uint), 1, cfg_files->request_edge);
			// fwrite(&to_index, sizeof(uint), 1, cfg_files->request_edge);

			try {
				int firstField;
				while (in.ready(0x10)) {
					firstField = in.readInt();
					if (firstField == REQUEST_HEADER_TAG) {
						totalRequests++;
						in.readInt();
						in.readInt();
						in.readInt();
						continue;
					}
					fromIndex = in.readInt();
					userLevel = (fromIndex >>> 26);
					fromIndex = (fromIndex & 0x3ffffff);
					callSite = establishCallSite(firstField, fromIndex);
					toRoutineHash = in.readInt();
					callSite.addEdge(toRoutineHash, userLevel);

					in.readInt();
				}
			} finally {
				in.close();
			}
			return totalRequests;
		}
	}

	private class RequestFileCollector extends SimpleFileVisitor<Path> {
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (file.getFileName().toString().equals("request-edge.run")) {
				File nodeFile = new File(file.getParent().toFile(), "node.run");
				if (!(nodeFile.exists() && nodeFile.isFile()))
					throw new AnalysisException("Cannot find the node.run file corresponding to %s",
							file.toAbsolutePath());
				requestSets.add(new RequestSet(file.toFile(), nodeFile));
			}
			return FileVisitResult.CONTINUE;
		}
	}

	private class ScriptNodeLoadContext implements ScriptNodeLoader.LoadContext {
		@Override
		public ScriptRoutineGraph getRoutine(int routineHash) {
			return routines.get(routineHash);
		}

		@Override
		public ScriptRoutineGraph createRoutine(int routineHash) {
			ScriptRoutineGraph routine = new ScriptRoutineGraph(routineHash, false);
			routines.put(routine.hash, routine);
			return routine;
		}
	}

	private static class RegularitySorter implements Comparator<CallSite> {
		@Override
		public int compare(CallSite first, CallSite second) {
			return ((int) (100000 * first.regularity)) - ((int) (100000 * second.regularity));
		}
	}

	private static final int REQUEST_HEADER_TAG = 2;

	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final RequestFileCollector requestFileCollector = new RequestFileCollector();
	private final List<RequestSet> requestSets = new ArrayList<RequestSet>();
	private final ScriptNodeLoader nodeLoader = new ScriptNodeLoader(new ScriptNodeLoadContext());

	private final Map<Integer, ScriptRoutineGraph> routines = new HashMap<Integer, ScriptRoutineGraph>();
	private final Map<CallSiteKey, CallSite> callSites = new HashMap<CallSiteKey, CallSite>();

	private EdgeRegularityAnalysis(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, verbose, watchlistFile, watchlistCategories);

	}

	private void run() {
		try {
			ScriptNode.init();

			argMap.parseOptions();

			Log.addOutput(System.out);
			Log.setLevel(Log.Level.values()[verbose.getValue()]);
			System.out.println("Log level " + verbose.getValue());

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}

			while (args.size() > 0) {
				File runDir = new File(args.pop());
				if (!(runDir.exists() && runDir.isDirectory()))
					throw new AnalysisException("Analysis target '%s' is not a directory.", runDir.getAbsolutePath());
				Files.walkFileTree(runDir.toPath(), requestFileCollector);
			}

			Log.log("Analyzing uncommon edges in %d request-edge.run files.", requestSets.size());

			int totalRequests = 0;
			for (RequestSet set : requestSets)
				totalRequests += set.load();
			Log.log("Loaded %d total requests to analyze", totalRequests);

			List<CallSite> callSitesByRegularity = new ArrayList<CallSite>();
			for (CallSite callSite : callSites.values()) {
				callSite.calculateRegularity();
				callSitesByRegularity.add(callSite);
			}
			Collections.sort(callSitesByRegularity, new RegularitySorter());
			for (CallSite callSite : callSitesByRegularity) {
				if (callSite.regularity > 0.5)
					break;
				Log.log("%02.03f%% 0x%x:", callSite.regularity * 100, callSite.routine.hash);
				for (Edge edge : callSite.edges)
					Log.log("\t%04d: -%d-> 0x%x", edge.count, edge.userLevel, edge.callee.hash);
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private CallSite establishCallSite(int routineHash, int nodeIndex) {
		CallSiteKey key = new CallSiteKey(routineHash, nodeIndex);
		CallSite site = callSites.get(key);
		if (site == null) {
			ScriptRoutineGraph routine = routines.get(routineHash);
			site = new CallSite(routine, routine.getNode(nodeIndex));
			callSites.put(key, site);
		}
		return site;
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s <run-dir> [ <run-dir> ... ]", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		EdgeRegularityAnalysis analysis = new EdgeRegularityAnalysis(stack);
		analysis.run();
	}
}
