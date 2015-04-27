package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;

public class ScriptRunFiles implements ScriptGraphDataFiles {

	public final File nodeFile;
	public final File opcodeEdgeFile;
	public final File routineEdgeFile;
	public final File routineCatalog;
	public final File request;
	public final File requestEdge;

	ScriptRunFiles(File directory) {
		nodeFile = new File(directory, "node.run");
		opcodeEdgeFile = new File(directory, "op-edge.run");
		routineEdgeFile = new File(directory, "routine-edge.run");
		routineCatalog = new File(directory, "routine-catalog.tab");
		request = new File(directory, "request.tab");
		requestEdge = new File(directory, "request-edge.run");

		if (!nodeFile.exists())
			throw new IllegalArgumentException("Invalid script run directory: node file " + nodeFile.getAbsolutePath()
					+ " does not exist");
	}

	@Override
	public String getDescription() {
		return "Script run from directory " + nodeFile.getParentFile().getName();
	}

	@Override
	public Type getType() {
		return Type.RUN;
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
