package edu.uci.eecs.scriptsafe.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineId;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptNodeLoader;

public class RequestGraph {

	private static class FileSet {

		private final File requestFile;
		private final File nodeFile;
		private final File datasetFile;
		private final File routineCatalog;

		FileSet(File requestFile, File nodeFile, File datasetFile, File routineCatalog) {
			this.requestFile = requestFile;
			this.nodeFile = nodeFile;
			this.datasetFile = datasetFile;
			this.routineCatalog = routineCatalog;
		}
	}

	private class RequestFileCollector extends SimpleFileVisitor<Path> {
		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (file.getFileName().toString().equals("request-edge.run")) {
				File routineCatalog = new File(file.getParent().toFile(), "routine-catalog.tab");
				if (!(routineCatalog.exists() && routineCatalog.isFile()))
					throw new AnalysisException("Cannot find the routine-catalog.tab file corresponding to %s",
							file.toAbsolutePath());
				File nodeFile = new File(file.getParent().toFile(), "node.run");
				File datasetFile = new File(file.getParent().toFile(), "cfg.set");
				if (nodeFile.exists() && nodeFile.isFile())
					fileSets.add(new FileSet(file.toFile(), nodeFile, null, routineCatalog));
				else if (datasetFile.exists() && datasetFile.isFile())
					fileSets.add(new FileSet(file.toFile(), null, datasetFile, routineCatalog));
				else
					throw new AnalysisException("Cannot find the node.run or cfg.set file corresponding to %s",
							file.toAbsolutePath());
			}
			return FileVisitResult.CONTINUE;
		}
	}

	public static class Loader {
		private class ScriptNodeLoadContext implements ScriptNodeLoader.LoadContext {
			private final RequestGraph requestGraph;

			ScriptNodeLoadContext(RequestGraph requestGraph) {
				this.requestGraph = requestGraph;
			}

			@Override
			public ScriptRoutineGraph getRoutine(int routineHash) {
				return requestGraph.routines.get(routineHash);
			}

			@Override
			public ScriptRoutineGraph createRoutine(int routineHash) {
				ScriptRoutineGraph routine = new ScriptRoutineGraph(routineHash,
						RoutineId.Cache.INSTANCE.getId(routineHash), false);
				requestGraph.routines.put(routine.hash, routine);
				return routine;
			}
		}

		private final List<Path> paths = new ArrayList<Path>();
		private final Map<Integer, Path> routineFiles = new HashMap<Integer, Path>();

		private ScriptNodeLoader nodeLoader;
		private final ScriptDatasetLoader cfgLoader = new ScriptDatasetLoader();

		private RequestGraph requestGraph;

		public void addPath(Path path) {
			paths.add(path);
		}

		public int getPathCount() {
			return paths.size();
		}

		public RequestGraph load() throws IOException {
			requestGraph = new RequestGraph();
			RequestFileCollector requestFileCollector = requestGraph.new RequestFileCollector();
			for (Path path : paths)
				Files.walkFileTree(path, requestFileCollector);

			nodeLoader = new ScriptNodeLoader(new ScriptNodeLoadContext(requestGraph));
			int totalRequests = 0;
			for (FileSet fileSet : requestGraph.fileSets)
				totalRequests += load(fileSet);

			Log.log("Loaded %d total requests to analyze", totalRequests);

			RequestGraph requestGraph = this.requestGraph;
			this.requestGraph = null;
			requestGraph.setTotalRequests(totalRequests);
			return requestGraph;
		}

		int load(FileSet fileSet) throws IOException {
			if (fileSet.nodeFile != null) {
				nodeLoader.loadNodes(fileSet.nodeFile);
			} else {
				ScriptFlowGraph cfg = new ScriptFlowGraph(Type.DATASET, fileSet.datasetFile.getAbsolutePath(), false);
				cfgLoader.loadDataset(fileSet.datasetFile, fileSet.routineCatalog, cfg);
				for (ScriptRoutineGraph routine : cfg.getRoutines())
					requestGraph.routines.put(routine.hash, routine);
			}

			LittleEndianInputStream in = new LittleEndianInputStream(fileSet.requestFile);
			int fromIndex, toRoutineHash, userLevel, totalRequests = 0;
			CallSite callSite;

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
					callSite = requestGraph.establishCallSite(
							RoutineId.Cache.INSTANCE.getId(fileSet.routineCatalog, firstField), firstField, fromIndex);
					toRoutineHash = in.readInt();
					callSite.addEdge(RoutineId.Cache.INSTANCE.getId(fileSet.routineCatalog, toRoutineHash),
							toRoutineHash, userLevel);

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

	public static class CallSiteKey {
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

	public class CallSite {
		final RoutineId id;
		final ScriptRoutineGraph routine;
		final ScriptNode node;
		final List<Edge> edges = new ArrayList<Edge>();

		double regularity = 0.0;

		CallSite(RoutineId id, ScriptRoutineGraph routine, ScriptNode node) {
			this.id = id;
			this.routine = routine;
			this.node = node;
		}

		void addEdge(RoutineId calleeId, int calleeHash, int userLevel) {
			Edge edge = null;
			ScriptRoutineGraph callee = routines.get(calleeHash);
			for (Edge e : edges) {
				if (e.matches(callee)) {
					edge = e;
					break;
				}
			}
			if (edge == null) {
				edge = new Edge(this, calleeId, callee);
				edges.add(edge);
			}
			if (userLevel < 2)
				edge.anonymousCount++;
			else
				edge.adminCount++;
		}

		void calculateRegularity() {
			if (edges.size() == 1) {
				regularity = 1.0;
				return;
			}

			int totalCalls = 0, maxCalls = 0, minCalls = Integer.MAX_VALUE, edgeCalls = 0;
			for (Edge edge : edges) {
				edgeCalls = (edge.adminCount + edge.anonymousCount);
				totalCalls += edgeCalls;
				if (edgeCalls > maxCalls)
					maxCalls = edgeCalls;
				if (edgeCalls < minCalls)
					minCalls = edgeCalls;
			}
			regularity = (minCalls / (double) maxCalls);
			if (regularity < 0.5) {
				double normalizer = 1 + (.5 / (double) Math.min(1, totalCalls / 10));
				regularity *= normalizer;
			}
		}
	}

	public class Edge {
		final CallSite callSite;
		final RoutineId calleeId;
		final ScriptRoutineGraph callee;

		int adminCount = 0;
		int anonymousCount = 0;

		Edge(CallSite callSite, RoutineId calleeId, ScriptRoutineGraph callee) {
			this.callSite = callSite;
			this.calleeId = calleeId;
			this.callee = callee;
		}

		boolean matches(ScriptRoutineGraph callee) {
			return this.callee.hash == callee.hash;
		}
	}

	private static final int REQUEST_HEADER_TAG = 2;

	private final List<FileSet> fileSets = new ArrayList<FileSet>();

	public final Map<Integer, ScriptRoutineGraph> routines = new HashMap<Integer, ScriptRoutineGraph>();
	public final Map<CallSiteKey, CallSite> callSites = new HashMap<CallSiteKey, CallSite>();
	public final Map<Integer, List<CallSite>> callSitesByRoutine = new HashMap<Integer, List<CallSite>>();
	public final Map<Path, List<ScriptRoutineGraph>> routinesBySourceFile = new HashMap<Path, List<ScriptRoutineGraph>>();

	private int totalRequests;

	public int getTotalRequests() {
		return totalRequests;
	}

	public void setTotalRequests(int totalRequests) {
		this.totalRequests = totalRequests;
	}

	private CallSite establishCallSite(RoutineId routineId, int routineHash, int nodeIndex) {
		CallSiteKey key = new CallSiteKey(routineHash, nodeIndex);
		CallSite site = callSites.get(key);
		if (site == null) {
			ScriptRoutineGraph routine = routines.get(routineHash);
			site = new CallSite(routineId, routine, routine.getNode(nodeIndex));
			callSites.put(key, site);
			establishRoutineCallSites(routineHash).add(site);
			establishFileRoutines(site.id.sourceFile);
		}
		return site;
	}

	private List<CallSite> establishRoutineCallSites(int routineHash) {
		List<CallSite> sites = callSitesByRoutine.get(routineHash);
		if (sites == null) {
			sites = new ArrayList<CallSite>();
			callSitesByRoutine.put(routineHash, sites);
		}
		return sites;
	}

	private List<ScriptRoutineGraph> establishFileRoutines(Path sourceFile) {
		List<ScriptRoutineGraph> routines = routinesBySourceFile.get(sourceFile);
		if (routines == null) {
			routines = new ArrayList<ScriptRoutineGraph>();
			routinesBySourceFile.put(sourceFile, routines);
		}
		return routines;
	}
}
