package edu.uci.plrg.cfi.php.analysis.request;

import java.io.File;

public class RequestFileSet {

	final File requestFile;
	final File nodeFile;
	final File datasetFile;
	final File routineCatalog;

	public RequestFileSet(File requestFile, File nodeFile, File datasetFile, File routineCatalog) {
		this.requestFile = requestFile;
		this.nodeFile = nodeFile;
		this.datasetFile = datasetFile;
		this.routineCatalog = routineCatalog;
	}
}
