package edu.uci.eecs.scriptsafe.merge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles;

public class RequestMerge {

	public static int peekRequestCount(File requestFile) throws NumberFormatException, IOException {
		RandomAccessFile tailReader = new RandomAccessFile(requestFile, "r");
		tailReader.seek(tailReader.length() - 9);
		int rightRequestCount = Integer.parseInt(tailReader.readLine());
		tailReader.close();
		return rightRequestCount;
	}

	private ScriptGraphDataFiles leftDataSource;
	private ScriptGraphDataFiles rightDataSource;

	public RequestMerge(ScriptGraphDataFiles leftDataSource, ScriptGraphDataFiles rightDataSource) {
		this.leftDataSource = leftDataSource;
		this.rightDataSource = rightDataSource;
	}

	public void merge(ScriptGraphDataFiles outputFiles) throws NumberFormatException, IOException {
		boolean inPlaceMerge = rightDataSource.getRequestFile().getAbsolutePath()
				.equals(outputFiles.getRequestFile().getAbsolutePath());
		File rightRequestFile = rightDataSource.getRequestFile();
		File rightEdgeFile = rightDataSource.getRequestEdgeFile().exists() ? rightDataSource.getRequestEdgeFile()
				: null;
		// File rightPersistenceFile = rightDataSource.getPersistenceFile();

		Log.message("Is in place? %b for '%s' vs. '%s'", inPlaceMerge, rightDataSource.getRequestFile(),
				outputFiles.getRequestFile());

		if (inPlaceMerge) {
			rightRequestFile = new File(rightDataSource.getRequestFile().getParentFile(), rightDataSource
					.getRequestFile().getName() + ".tmp");
			if (rightEdgeFile != null) {
				rightEdgeFile = new File(rightDataSource.getRequestEdgeFile().getParentFile(), rightDataSource
						.getRequestEdgeFile().getName() + ".tmp");
			}
			// rightPersistenceFile = new File(rightDataSource.getPersistenceFile().getParentFile(), rightDataSource
			// .getPersistenceFile().getName() + ".tmp");
			Log.log("Moving right request file to '%s' for in-place merge", rightRequestFile.getAbsolutePath());
			rightDataSource.getRequestFile().renameTo(rightRequestFile);
			if (rightEdgeFile != null)
				rightDataSource.getRequestEdgeFile().renameTo(rightEdgeFile);
			// rightDataSource.getPersistenceFile().renameTo(rightPersistenceFile);
		}

		FileOutputStream requestOutput = new FileOutputStream(outputFiles.getRequestFile());
		RequestParser leftRequestParser = new RequestParser(new FileInputStream(leftDataSource.getRequestFile()),
				requestOutput);
		RequestParser rightRequestParser = new RequestParser(new FileInputStream(rightRequestFile), requestOutput);

		LittleEndianOutputStream requestEdgeOutput = null;
		RequestEdgeParser leftEdgeParser = null;
		RequestEdgeParser rightEdgeParser = null;

		if (rightEdgeFile != null) {
			requestEdgeOutput = new LittleEndianOutputStream(new FileOutputStream(outputFiles.getRequestEdgeFile(),
					true), "file:" + outputFiles.getRequestEdgeFile().getAbsolutePath());
			leftEdgeParser = new RequestEdgeParser(new LittleEndianInputStream(leftDataSource.getRequestEdgeFile()),
					requestEdgeOutput);
			rightEdgeParser = new RequestEdgeParser(new LittleEndianInputStream(rightEdgeFile), requestEdgeOutput);
		}

		/**
		 * <pre>
		FileOutputStream persistenceOutput = new FileOutputStream(outputFiles.getPersistenceFile());
		PersistenceParser leftPersistenceParser = new PersistenceParser(new FileInputStream(
				leftDataSource.getPersistenceFile()), persistenceOutput);
		PersistenceParser rightPersistenceParser = new PersistenceParser(new FileInputStream(rightPersistenceFile),
				persistenceOutput);
		 */

		int nextRequestId = 0;
		leftRequestParser.readRequestStart();
		rightRequestParser.readRequestStart();
		do {
			if (rightRequestParser.eof
					|| (!leftRequestParser.eof && leftRequestParser.timestamp < rightRequestParser.timestamp)) {
				Log.message("Merge left request");
				leftRequestParser.writeNextRequest(nextRequestId);
				leftRequestParser.readRequestStart();
				if (rightEdgeFile != null)
					leftEdgeParser.writeNextRequest(nextRequestId);
				// leftPersistenceParser.writeNextRequest();
			} else {
				Log.message("Merge right request");
				rightRequestParser.writeNextRequest(nextRequestId);
				rightRequestParser.readRequestStart();
				if (rightEdgeFile != null)
					rightEdgeParser.writeNextRequest(nextRequestId);
				// rightPersistenceParser.writeNextRequest();
			}
			if (++nextRequestId == Integer.MAX_VALUE) {
				nextRequestId = 0;
				Log.warn("Warning: request count exceeds maximum %d. Resetting to zero.", Integer.MAX_VALUE);
			}
		} while (!(leftRequestParser.eof && rightRequestParser.eof));

		leftRequestParser.close();
		rightRequestParser.close();
		requestOutput.flush();
		requestOutput.close();
		if (rightEdgeFile != null) {
			leftEdgeParser.close();
			rightEdgeParser.close();
			requestEdgeOutput.flush();
			requestEdgeOutput.close();
		}

		if (inPlaceMerge) {
			rightRequestFile.delete();
			if (rightEdgeFile != null)
				rightEdgeFile.delete();
		}
	}
}
