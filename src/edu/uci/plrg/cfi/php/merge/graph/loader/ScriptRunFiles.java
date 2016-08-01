package edu.uci.plrg.cfi.php.merge.graph.loader;

import java.io.File;

import edu.uci.plrg.cfi.php.merge.graph.ScriptDataFilename;

public class ScriptRunFiles implements ScriptGraphDataFiles {

	public final File nodeFile;
	public final File opcodeEdgeFile;
	public final File routineEdgeFile;
	public final File routineCatalog;
	public final File request;
	public final File requestEdge;
	public final File persistence;
	public final File opcodes;

	ScriptRunFiles(File directory) {
		nodeFile = ScriptDataFilename.NODE.getFile(directory);
		opcodeEdgeFile = ScriptDataFilename.OPCODE_EDGE.getFile(directory);
		routineEdgeFile = ScriptDataFilename.ROUTINE_EDGE.getFile(directory);
		routineCatalog = ScriptDataFilename.ROUTINE_CATALOG.getFile(directory);
		request = ScriptDataFilename.REQUEST_FIELDS.getFile(directory);
		requestEdge = ScriptDataFilename.REQUEST_GRAPH.getFile(directory);
		persistence = ScriptDataFilename.PERSISTENCE.getFile(directory);
		opcodes = ScriptDataFilename.OPCODES.getFile(directory);

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
	
	@Override
	public File getPersistenceFile() {
		return persistence;
	}
	
	@Override
	public File getOpcodesFile() {
		return opcodes;
	}
}
