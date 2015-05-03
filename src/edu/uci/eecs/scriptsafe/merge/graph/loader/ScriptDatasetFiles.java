package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;

public class ScriptDatasetFiles implements ScriptGraphDataFiles {

	public final File directory;
	
	public final File dataset;
	public final File routineCatalog;
	public final File request;
	public final File requestEdge;
	
	public final File mergeLog;

	ScriptDatasetFiles(File directory) {
		this.directory = directory;
		directory.mkdir();
		dataset = new File(directory, "cfg.set");
		routineCatalog = new File(directory, "routine-catalog.tab");
		request = new File(directory, "request.tab");
		requestEdge = new File(directory, "request-edge.run");
		mergeLog = new File(directory, "merge.log");
	}
	
	public boolean exists() {
		return dataset.exists();
	}

	@Override
	public String getDescription() {
		return "Script dataset from file " + directory.getName();
	}

	@Override
	public Type getType() {
		return Type.DATASET;
	}
	
	@Override
	public File getRequestFile() {
		return request;
	}
	
	@Override
	public File getRequestEdgeFile() {
		return requestEdge;
	}
	
	@Override
	public File getRoutineCatalogFile() {
		return routineCatalog;
	}
}
