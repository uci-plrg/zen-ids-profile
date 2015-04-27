package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;

public class RequestMerge {

	private ScriptGraphDataFiles leftDataSource;
	private ScriptGraphDataFiles rightDataSource;

	public RequestMerge(ScriptGraphDataFiles leftDataSource, ScriptGraphDataFiles rightDataSource) {
		this.leftDataSource = leftDataSource;
		this.rightDataSource = rightDataSource;
	}

	public void merge(ScriptGraphDataFiles outputFiles) throws NumberFormatException, IOException {
		if (!rightDataSource.getRequestFile().equals(outputFiles.getRequestFile())) {
			Files.copy(rightDataSource.getRequestFile().toPath(), outputFiles.getRequestFile().toPath());
			Files.copy(rightDataSource.getRequestEdgeFile().toPath(), outputFiles.getRequestEdgeFile().toPath());
		}
		RandomAccessFile tailReader = new RandomAccessFile(outputFiles.getRequestFile(), "r");
		tailReader.seek(tailReader.length() - 9);
		int requestCount = Integer.parseInt(tailReader.readLine());
		tailReader.close();

		appendRequests(requestCount + 1, outputFiles.getRequestFile());
		appendRequestEdges();
	}

	private void appendRequests(int nextRequestId, File outputFile) throws IOException {
		int length, set;
		byte buffer[] = new byte[8192];
		FileInputStream in = new FileInputStream(leftDataSource.getRequestFile());
		FileOutputStream out = new FileOutputStream(outputFile, true);
		try {
			set = 0;
			while (in.available() > 0) {
				length = in.read(buffer);
				for (int i = set; i < length; i++) {
					if (buffer[i] == '|') {
						out.write(buffer, set, (i - set) + 1);
						out.write(String.format("%08d", nextRequestId++).getBytes());
						set = i + 9;
						i += 8;
					}
				}
				if (set < length) {
					out.write(buffer, set, length - set);
				} else if (set > length) {
					set = set - length;
				}
			}
		} finally {
			in.close();
			out.flush();
			out.close();
		}
	}

	private void appendRequestEdges() {

	}
}
