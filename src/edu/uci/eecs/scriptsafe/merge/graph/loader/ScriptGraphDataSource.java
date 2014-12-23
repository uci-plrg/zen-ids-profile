package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;

public interface ScriptGraphDataSource {

	Type getType();
	
	public enum Type {
		RUN,
		DATASET;
	}
	
	public static class Factory {
		public static ScriptGraphDataSource construct(File path) {
			if (!path.exists())
				throw new IllegalArgumentException("Invalid script data source: " + path + " does not exist");
			if (path.isDirectory())
				return new ScriptRunFileSet(path);
			else
				return new ScriptDatasetFile(path);
		}
	}
}
