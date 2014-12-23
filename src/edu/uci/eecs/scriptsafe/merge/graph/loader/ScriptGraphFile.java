package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;

public class ScriptGraphFile {

	public final File file;

	public ScriptGraphFile(String path) {
		file = new File(path);
		if (!file.exists())
			throw new IllegalArgumentException("Invalid script graph path: " + file.getAbsolutePath()
					+ " does not exist");
		int dot = path.lastIndexOf('.');
		if (dot < 0)
			throw new IllegalArgumentException("Invalid script graph path: " + file.getAbsolutePath()
					+ " has no file extension");
		String extension = path.substring(dot + 1);
		if (!extension.equalsIgnoreCase("set"))
			throw new IllegalArgumentException("Invalid script graph path: " + file.getAbsolutePath()
					+ " must have file extension .set");
	}
}
