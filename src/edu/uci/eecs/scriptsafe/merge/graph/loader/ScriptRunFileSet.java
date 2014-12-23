package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;

public class ScriptRunFileSet {

	public final File nodeFile;
	public final File opcodeEdgeFile;
	public final File routineEdgeFile;

	public ScriptRunFileSet(String path) {
		File directory = new File(path);

		if (!directory.exists())
			throw new IllegalArgumentException("Invalid script run directory: " + path + " does not exist");

		nodeFile = new File(directory, "node.run");
		opcodeEdgeFile = new File(directory, "op-edge.run");
		routineEdgeFile = new File(directory, "routine-edge.run");

		if (!nodeFile.exists())
			throw new IllegalArgumentException("Invalid script run directory: node file " + nodeFile.getAbsolutePath()
					+ " does not exist");
	}
}
