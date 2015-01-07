package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;

public class ScriptRunFileSet implements ScriptGraphDataSource {

	public final File nodeFile;
	public final File opcodeEdgeFile;
	public final File routineEdgeFile;

	ScriptRunFileSet(File directory) {
		nodeFile = new File(directory, "node.run");
		opcodeEdgeFile = new File(directory, "op-edge.run");
		routineEdgeFile = new File(directory, "routine-edge.run");

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
}
