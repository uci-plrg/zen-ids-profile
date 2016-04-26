package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;

import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;

public class ScriptDatasetFiles implements ScriptGraphDataFiles {

	public final File directory;

	public final File dataset;
	public final File routineCatalog;
	public final File request;
	public final File requestEdge;
	public final File persistence;
	public final File opcodes;

	public final File mergeLog;

	ScriptDatasetFiles(File directory) {
		this.directory = directory;

		directory.mkdir();
		dataset = ScriptDataFilename.CFG.getFile(directory);
		routineCatalog = ScriptDataFilename.ROUTINE_CATALOG.getFile(directory);
		request = ScriptDataFilename.REQUEST_FIELDS.getFile(directory);
		requestEdge = ScriptDataFilename.REQUEST_GRAPH.getFile(directory);
		mergeLog = ScriptDataFilename.MERGE_LOG.getFile(directory);
		persistence = ScriptDataFilename.PERSISTENCE.getFile(directory);
		opcodes = ScriptDataFilename.OPCODES.getFile(directory);
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
	
	@Override
	public File getPersistenceFile() {
		return persistence;
	}
	
	@Override
	public File getOpcodesFile() {
		return opcodes;
	}
}
