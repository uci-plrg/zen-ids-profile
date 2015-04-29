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
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptNodeLoader;

public class RequestGraph {
	private static class FileSet {
		private final File requestFile;
		private final File nodeFile;
		private final File routineCatalog;

		FileSet(File requestFile, File nodeFile, File routineCatalog) {
			this.requestFile = requestFile;
			this.nodeFile = nodeFile;
			this.routineCatalog = routineCatalog;
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
				File routineCatalog = new File(file.getParent().toFile(), "routine-catalog.tab");
				if (!(routineCatalog.exists() && routineCatalog.isFile()))
					throw new AnalysisException("Cannot find the routine-catalog.tab file corresponding to %s",
							file.toAbsolutePath());
				fileSets.add(new FileSet(file.toFile(), nodeFile, routineCatalog));
			}
			return FileVisitResult.CONTINUE;
		}
	}

	static class Loader {
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
				ScriptRoutineGraph routine = new ScriptRoutineGraph(routineHash, false);
				requestGraph.routines.put(routine.hash, routine);
				return routine;
			}
		}

		private final List<Path> paths = new ArrayList<Path>();
		private final Map<Integer, String> routineNames = new HashMap<Integer, String>();
		private ScriptNodeLoader nodeLoader;

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
			for (Path path : paths) {
				Files.walkFileTree(path, requestFileCollector);
			}

			nodeLoader = new ScriptNodeLoader(new ScriptNodeLoadContext(requestGraph));
			int totalRequests = 0;
			for (FileSet fileSet : requestGraph.fileSets)
				totalRequests += load(fileSet);

			Log.log("Loaded %d total requests to analyze", totalRequests);

			RequestGraph requestGraph = this.requestGraph;
			this.requestGraph = null;
			return requestGraph;
		}

		int load(FileSet fileSet) throws IOException {
			nodeLoader.loadNodes(fileSet.nodeFile);

			LittleEndianInputStream in = new LittleEndianInputStream(fileSet.requestFile);
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
					callSite = requestGraph.establishCallSite(getRoutineName(fileSet.routineCatalog, firstField),
							firstField, fromIndex);
					toRoutineHash = in.readInt();
					callSite.addEdge(getRoutineName(fileSet.routineCatalog, toRoutineHash), toRoutineHash, userLevel);

					in.readInt();
				}
			} finally {
				in.close();
			}
			return totalRequests;
		}

		String getRoutineName(File routineCatalog, int hash) throws IOException {
			String name = routineNames.get(hash);
			if (name == null) {
				BufferedReader in = new BufferedReader(new FileReader(routineCatalog));
				try {
					String line;
					int space;
					while ((line = in.readLine()) != null) {
						space = line.trim().indexOf(" ");
						if (space < 0)
							continue;
						routineNames.put(Integer.parseInt(line.substring(2, space), 16), line.substring(space + 1));
					}
				} finally {
					in.close();
				}
				name = routineNames.get(hash);
			}
			return name;
		}
	}

	static class CallSiteKey {
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

	class CallSite {
		final String name;
		final ScriptRoutineGraph routine;
		final ScriptNode node;
		final List<Edge> edges = new ArrayList<Edge>();

		double regularity = 0.0;

		CallSite(String name, ScriptRoutineGraph routine, ScriptNode node) {
			this.name = name;
			this.routine = routine;
			this.node = node;
		}

		void addEdge(String calleeName, int calleeHash, int userLevel) {
			ScriptRoutineGraph callee = routines.get(calleeHash);
			boolean found = false;
			for (Edge edge : edges) {
				if (edge.matches(callee, userLevel)) {
					edge.count++;
					found = true;
				}
			}
			if (!found)
				edges.add(new Edge(this, calleeName, callee, userLevel));
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

	class Edge {
		final CallSite callSite;
		final int userLevel;
		final String calleeName;
		final ScriptRoutineGraph callee;

		int count = 1;

		Edge(CallSite callSite, String calleeName, ScriptRoutineGraph callee, int userLevel) {
			this.callSite = callSite;
			this.userLevel = userLevel;
			this.calleeName = calleeName;
			this.callee = callee;
		}

		boolean matches(ScriptRoutineGraph callee, int userLevel) {
			return this.callee.hash == callee.hash && this.userLevel == userLevel;
		}
	}

	private static final int REQUEST_HEADER_TAG = 2;

	private final List<FileSet> fileSets = new ArrayList<FileSet>();

	final Map<Integer, ScriptRoutineGraph> routines = new HashMap<Integer, ScriptRoutineGraph>();
	final Map<CallSiteKey, CallSite> callSites = new HashMap<CallSiteKey, CallSite>();

	private CallSite establishCallSite(String routineName, int routineHash, int nodeIndex) {
		CallSiteKey key = new CallSiteKey(routineHash, nodeIndex);
		CallSite site = callSites.get(key);
		if (site == null) {
			ScriptRoutineGraph routine = routines.get(routineHash);
			site = new CallSite(routineName, routine, routine.getNode(nodeIndex));
			callSites.put(key, site);
		}
		return site;
	}
}
