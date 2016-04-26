package edu.uci.eecs.scriptsafe.merge.graph;

import java.io.File;
import java.nio.file.Path;

import edu.uci.eecs.scriptsafe.ScriptSafeException;

public enum ScriptDataFilename {
	NODE("node.run"),
	OPCODE_EDGE("op-edge.run"),
	ROUTINE_EDGE("routine-edge.run"),
	CFG("cfg.set"),
	ROUTINE_CATALOG("routine-catalog.tab"),
	REQUEST_FIELDS("request.tab"),
	REQUEST_GRAPH("request-edge.run"),
	PERSISTENCE("persistence.log"),
	OPCODES("opcodes.log"),
	MERGE_LOG("merge.log");

	public final String filename;

	private ScriptDataFilename(String filename) {
		this.filename = filename;
	}

	public File getFile(File directory) {
		return new File(directory, filename);
	}

	public File requireFile(File directory) {
		File file = new File(directory, filename);
		if (!(file.exists() && file.isFile()))
			throw new ScriptSafeException("Cannot find dataset file '%s'", file.getAbsolutePath());
		return file;
	}

	public boolean matches(Path path) {
		return path.getFileName().toString().equals(filename);
	}
}
