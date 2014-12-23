package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;

public class ScriptDatasetFile implements ScriptGraphDataSource {

	public final File file;

	ScriptDatasetFile(File file) {
		this.file = file;
		String filename = file.getName();
		int dot = filename.lastIndexOf('.');
		if (dot < 0)
			throw new IllegalArgumentException("Invalid script graph path: " + file.getAbsolutePath()
					+ " has no file extension");
		String extension = filename.substring(dot + 1);
		if (!extension.equalsIgnoreCase("set"))
			throw new IllegalArgumentException("Invalid script graph path: " + file.getAbsolutePath()
					+ " must have file extension .set");
	}
	
	@Override
	public Type getType() {
		return Type.DATASET;
	}
}
