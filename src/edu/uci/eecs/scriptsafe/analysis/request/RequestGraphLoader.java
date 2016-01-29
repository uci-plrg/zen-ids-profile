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

	private class RequestFileCollector extends SimpleFileVisitor<Path> {
		private final List<RequestFileSet> fileSets = new ArrayList<RequestFileSet>();

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
			if (ScriptDataFilename.REQUEST_GRAPH.matches(file)) {
				File routineCatalog = ScriptDataFilename.ROUTINE_CATALOG.requireFile(file.getParent().toFile());
				File nodeFile = ScriptDataFilename.NODE.getFile(file.getParent().toFile());
				File datasetFile = ScriptDataFilename.CFG.getFile(file.getParent().toFile());
				if (nodeFile.exists() && nodeFile.isFile())
					fileSets.add(new RequestFileSet(file.toFile(), nodeFile, null, routineCatalog));
				else if (datasetFile.exists() && datasetFile.isFile())
					fileSets.add(new RequestFileSet(file.toFile(), null, datasetFile, routineCatalog));
				else
					throw new AnalysisException("Cannot find the %s or %s file corresponding to %s",
							ScriptDataFilename.NODE.filename, ScriptDataFilename.CFG.filename, file.toAbsolutePath());
			}
			return FileVisitResult.CONTINUE;
		}
	}

	private final List<Path> paths = new ArrayList<Path>();
	private final Map<Integer, Path> routineFiles = new HashMap<Integer, Path>();

	private RequestGraph requestGraph;

	public void addPath(Path path) {
		paths.add(path);
	}

	public int getPathCount() {
		return paths.size();
	}

	public RequestGraph load(RequestGraph requestGraph) throws IOException {
		this.requestGraph = requestGraph;
		RequestFileCollector requestFileCollector = new RequestFileCollector();
		for (Path path : paths)
			Files.walkFileTree(path, requestFileCollector);

		int totalRequests = 0;
		ScriptNodeLoader nodeLoader = new ScriptNodeLoader();
		ScriptDatasetLoader cfgLoader = new ScriptDatasetLoader();
		for (RequestFileSet fileSet : requestFileCollector.fileSets) {
			nodeLoader.setLoadContext(requestGraph);
			if (fileSet.nodeFile != null) {
				nodeLoader.loadNodes(fileSet.nodeFile);
			} else {
				ScriptFlowGraph cfg = new ScriptFlowGraph(Type.DATASET, fileSet.datasetFile.getAbsolutePath(), false);
				cfgLoader.loadDataset(fileSet.datasetFile, fileSet.routineCatalog, cfg, false);
				for (ScriptRoutineGraph routine : cfg.getRoutines())
					requestGraph.addRoutine(routine);
			}
			totalRequests += RequestSequenceLoader.load(fileSet, requestGraph);
		}

		Log.log("Loaded %d total requests to analyze", totalRequests);

		this.requestGraph = null;
		requestGraph.setTotalRequests(totalRequests);
		return requestGraph;
	}
}
