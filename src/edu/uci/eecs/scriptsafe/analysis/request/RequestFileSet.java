package edu.uci.eecs.scriptsafe.analysis.request;

import java.io.File;

class RequestFileSet {
	final File requestFile;
	final File nodeFile;
	final File datasetFile;
	final File routineCatalog;

	RequestFileSet(File requestFile, File nodeFile, File datasetFile, File routineCatalog) {
		this.requestFile = requestFile;
		this.nodeFile = nodeFile;
		this.datasetFile = datasetFile;
		this.routineCatalog = routineCatalog;
	}
}
