package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;
import java.io.IOException;

import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;

public interface ScriptGraphDataFiles {

	String getDescription();

	Type getType();

	File getRequestFile();

	File getRequestEdgeFile();

	File getRoutineCatalogFile();

	public enum Type {
		RUN,
		DATASET;
	}

	public static class Factory {
		public static ScriptGraphDataFiles bind(File path) throws IOException {
			path = path.getCanonicalFile();
			if (!path.exists())
				throw new IllegalArgumentException("Invalid script data source: " + path + " does not exist");
			if (!path.isDirectory())
				throw new IllegalArgumentException("Invalid script data source: " + path + " is not a directory");

			if (ScriptDataFilename.NODE.getFile(path).exists())
				return new ScriptRunFiles(path);
			else if (ScriptDataFilename.CFG.getFile(path).exists())
				return new ScriptDatasetFiles(path);
			else
				throw new IllegalArgumentException("Invalid script data source: " + path + " contains no data");
		}

		public static ScriptDatasetFiles construct(File path) {
			if (path.exists() && !path.isDirectory())
				throw new IllegalArgumentException("Invalid script data source: " + path + " is not a directory");

			return new ScriptDatasetFiles(path);
		}
	}
}
