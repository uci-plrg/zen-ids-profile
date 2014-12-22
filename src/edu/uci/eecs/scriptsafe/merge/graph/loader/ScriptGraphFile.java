package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;

public class ScriptGraphFile {

	public enum Type {
		RUN("Run", "run"),
		DATASET("Dataset", "set");

		public final String name;
		public final String extension;

		private Type(String name, String extension) {
			this.name = name;
			this.extension = extension;
		}
	}

	public static ScriptGraphFile fromPath(String path) {
		File file = new File(path);
		if (!file.exists())
			throw new IllegalArgumentException("Path " + file.getAbsolutePath() + " does not exist.");
		int dot = path.lastIndexOf('.');
		if (dot < 0)
			throw new IllegalArgumentException("Path " + file.getAbsolutePath()
					+ " is not a valid script graph: it has no file extension.");
		String extension = path.substring(dot + 1);
		if (extension.equalsIgnoreCase(Type.RUN.extension))
			return new ScriptGraphFile(file, Type.RUN);
		else if (extension.equalsIgnoreCase(Type.DATASET.extension))
			return new ScriptGraphFile(file, Type.DATASET);
		else
			throw new IllegalArgumentException("Path " + file.getAbsolutePath()
					+ " is not a valid script graph: it has an invalid file extension (must be .run or .set).");
	}

	public final File file;
	public final Type type;

	public ScriptGraphFile(File file, Type type) {
		this.file = file;
		this.type = type;
	}
}
