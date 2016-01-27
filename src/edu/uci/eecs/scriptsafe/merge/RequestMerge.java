package edu.uci.eecs.scriptsafe.merge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles;

public class RequestMerge {

	public static int getRequestCount(File requestFile) throws NumberFormatException, IOException {
		RandomAccessFile tailReader = new RandomAccessFile(requestFile, "r");
		tailReader.seek(tailReader.length() - 9);
		int rightRequestCount = Integer.parseInt(tailReader.readLine());
		tailReader.close();
		return rightRequestCount;
	}

	private static final int REQUEST_HEADER_TAG = 2;

	private ScriptGraphDataFiles leftDataSource;
	private ScriptGraphDataFiles rightDataSource;

	public RequestMerge(ScriptGraphDataFiles leftDataSource, ScriptGraphDataFiles rightDataSource) {
		this.leftDataSource = leftDataSource;
		this.rightDataSource = rightDataSource;
	}

	public void merge(ScriptGraphDataFiles outputFiles) throws NumberFormatException, IOException {
		if (!rightDataSource.getRequestFile().equals(outputFiles.getRequestFile())) {
			Files.copy(rightDataSource.getRequestFile().toPath(), outputFiles.getRequestFile().toPath(),
					StandardCopyOption.REPLACE_EXISTING);
			Files.copy(rightDataSource.getRequestEdgeFile().toPath(), outputFiles.getRequestEdgeFile().toPath(),
					StandardCopyOption.REPLACE_EXISTING);
		}
		int rightRequestCount = RequestMerge.getRequestCount(outputFiles.getRequestFile());

		int requestCount = appendRequests(rightRequestCount + 1, outputFiles.getRequestFile());
		int requestSubgraphCount = appendRequestEdges(rightRequestCount + 1, outputFiles.getRequestEdgeFile());
		// if (requestCount != requestSubgraphCount)
		// Log.warn("Request count %d does not match request subgraph count %d", requestCount, requestSubgraphCount);
	}

	private int appendRequests(int nextRequestId, File outputFile) throws IOException {
		int length, set, fileRemaining;
		byte buffer[] = new byte[8192];
		FileInputStream in = new FileInputStream(leftDataSource.getRequestFile());
		FileOutputStream out = new FileOutputStream(outputFile, true);
		try {
			set = 0;
			fileRemaining = in.available();
			while (fileRemaining > 0) {
				length = in.read(buffer);
				fileRemaining -= length;
				for (int i = set; i < length; i++) {
					if (buffer[i] == '|' && isRequestId(buffer, i, (length - (i + 1)) + fileRemaining)) {
						if (nextRequestId == Integer.MAX_VALUE) {
							nextRequestId = 0;
							Log.log("Warning: request count exceeds maximum %d. Resetting to zero.", Integer.MAX_VALUE);
						}
						out.write(buffer, set, (i - set) + 1);
						out.write(String.format("%08d", nextRequestId++).getBytes());
						set = i + 9;
						i += 8;
					}
				}
				if (set < length) {
					out.write(buffer, set, length - set);
				}
				if (set > length)
					set = set - length;
				else
					set = 0;
			}
		} finally {
			in.close();
			out.flush();
			out.close();
		}
		return nextRequestId;
	}

	private int appendRequestEdges(int nextRequestId, File outputFile) throws IOException {
		LittleEndianInputStream in = new LittleEndianInputStream(leftDataSource.getRequestEdgeFile());
		LittleEndianOutputStream out = new LittleEndianOutputStream(new FileOutputStream(outputFile, true), "file:"
				+ outputFile.getAbsolutePath());

		int total = 0;
		try {
			int firstField;
			while (in.ready(0x10)) {
				firstField = in.readInt();
				if (firstField == REQUEST_HEADER_TAG) {
					if (nextRequestId == Integer.MAX_VALUE) {
						nextRequestId = 0;
						Log.log("Warning: request count exceeds maximum %d. Resetting to zero.", Integer.MAX_VALUE);
					}
					out.writeInt(firstField);
					out.writeInt(nextRequestId++);
					in.readInt();
				} else {
					out.writeInt(firstField);
					out.writeInt(in.readInt());
				}
				out.writeInt(in.readInt());
				out.writeInt(in.readInt());
				total++;
			}
		} finally {
			in.close();
			out.flush();
			out.close();
		}
		return total;
	}

	private boolean isRequestId(byte buffer[], int i, int uncheckedBytes) {
		if (uncheckedBytes < 10)
			return true;
		if (i >= 13) {
			return new String(Arrays.copyOfRange(buffer, i - 13, i - 1)).equals("<request-id>");
		} else {
			return new String(Arrays.copyOfRange(buffer, i + 9, i + 21)).equals("\n<client-ip>");
		}
	}
}
