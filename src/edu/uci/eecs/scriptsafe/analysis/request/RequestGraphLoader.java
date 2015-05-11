package edu.uci.eecs.scriptsafe.analysis.request;

import java.io.File;
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
import edu.uci.eecs.scriptsafe.analysis.AnalysisException;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineId;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptNodeLoader;

public class RequestGraphLoader {

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
		private final List<FileSet> fileSets = new ArrayList<FileSet>();

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (ScriptDataFilename.REQUEST_GRAPH.matches(file)) {
				File routineCatalog = ScriptDataFilename.ROUTINE_CATALOG.requireFile(file.getParent().toFile());
				File nodeFile = ScriptDataFilename.NODE.requireFile(file.getParent().toFile());
				File datasetFile = ScriptDataFilename.CFG.getFile(file.getParent().toFile());
				if (nodeFile.exists() && nodeFile.isFile())
					fileSets.add(new FileSet(file.toFile(), nodeFile, null, routineCatalog));
				else if (datasetFile.exists() && datasetFile.isFile())
					fileSets.add(new FileSet(file.toFile(), null, datasetFile, routineCatalog));
				else
					throw new AnalysisException("Cannot find the %s or %s file corresponding to %s",
							ScriptDataFilename.NODE.filename, ScriptDataFilename.CFG.filename, file.toAbsolutePath());
			}
			return FileVisitResult.CONTINUE;
		}
	}

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

	private static final int REQUEST_HEADER_TAG = 2;

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
		RequestFileCollector requestFileCollector = new RequestFileCollector();
		for (Path path : paths)
			Files.walkFileTree(path, requestFileCollector);

		nodeLoader = new ScriptNodeLoader(new ScriptNodeLoadContext(requestGraph));
		int totalRequests = 0;
		for (FileSet fileSet : requestFileCollector.fileSets)
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
		RequestCallSiteSummary callSite;

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
						requestGraph.routines.get(toRoutineHash), userLevel);

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
