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

	private static class RequestParser {

		static final int BUFFER_SIZE = 8192;
		static final int BUFFER_TAIL_SIZE = 0x10;

		final FileInputStream in;
		final FileOutputStream out;

		long timestamp = 0;
		int nextRequestId;
		boolean eof = false;

		StringBuilder requestStart = new StringBuilder(), tempString = new StringBuilder();
		int i, currentBufferLength, fileRemaining;
		byte buffer[] = null;
		String lastBufferTail = null;

		public RequestParser(FileInputStream in, FileOutputStream out) throws IOException {
			this.in = in;
			this.out = out;

			fileRemaining = in.available();
			readBuffer();
		}

		boolean readBuffer() throws IOException {
			if (fileRemaining <= 0)
				return false;

			if (buffer == null)
				buffer = new byte[BUFFER_SIZE];
			else
				lastBufferTail = new String(buffer, BUFFER_SIZE - BUFFER_TAIL_SIZE, BUFFER_TAIL_SIZE);
			currentBufferLength = in.read(buffer);
			fileRemaining -= currentBufferLength;
			i = 0;
			return true;
		}

		String getPreviousSnippet() {
			String previousSnippet = null;
			if (i < BUFFER_TAIL_SIZE) {
				if (lastBufferTail != null) /* else not a match */
					previousSnippet = lastBufferTail.substring(i) + new String(buffer, 0, i);
			} else {
				previousSnippet = new String(buffer, i - BUFFER_TAIL_SIZE, BUFFER_TAIL_SIZE);
			}
			return previousSnippet;
		}

		String readString(int readLength) throws IOException {
			String s;
			if (currentBufferLength - i > readLength) {
				s = new String(buffer, i, readLength);
				i += readLength;
			} else {
				String start = new String(buffer, i, currentBufferLength - i);
				if (readBuffer()) {
					s = start + new String(buffer, 0, readLength - start.length());
					i += (readLength - start.length());
				} else {
					s = start;
				}
			}
			return s;
		}

		String readString(char delimiter) throws IOException {
			tempString.setLength(0);

			do {
				for (; i < currentBufferLength; i++) {
					if (buffer[i] == delimiter)
						return tempString.toString();
					else
						tempString.append((char) buffer[i]);
				}
			} while (readBuffer());

			return tempString.toString();
		}

		void readRequestStart() throws IOException {
			int set = i;

			do {
				for (; i < currentBufferLength; i++) {
					if (buffer[i] == '|' && isRequestTime(getPreviousSnippet())) {
						String setToEnd = null;
						if (i > BUFFER_SIZE - BUFFER_TAIL_SIZE) /* may flip */
							setToEnd = new String(buffer, set, BUFFER_SIZE - set);
						timestamp = Long.parseLong(readString('\n').substring(3), 16);
						Log.log("Found request time at %d: 0x%x. Set is %d.", i, timestamp, set);
						if (i < set) { /* did flip */
							requestStart.append(setToEnd);
							set = 0;
						}
						requestStart.append(new String(buffer, set, i - set));
						return;
					}
				}
				requestStart.append(new String(buffer, set, currentBufferLength - set));
				set = 0;
			} while (readBuffer());
		}

		void writeNextRequest(int requestId) throws IOException {
			Log.log("Writing request start of %d bytes", requestStart.length());
			out.write(requestStart.toString().getBytes());
			requestStart.setLength(0);

			do {
				int set = i;
				for (; i < currentBufferLength; i++) {
					if (buffer[i] == '|') {
						if (isRequestId(getPreviousSnippet())) {
							Log.log("Found request id at %d. Set is at %d, buffer has %d bytes.", i, set,
									currentBufferLength);
							Log.log("Writing [%d,%d] plus request id", set, i + 1);
							out.write(buffer, set, (i - set) + 1);
							out.write(String.format("%08d", requestId).getBytes());

							set = i + 9;
							if (set + 1 >= currentBufferLength && fileRemaining == 0) {
								Log.log("eof");
								eof = true;
							} else if (set >= currentBufferLength) {
								set = set - currentBufferLength;
								readBuffer();
							}
							i = set - 1;
							return;
						}
					}
				}
				if (set < currentBufferLength) {
					Log.log("Writing [%d,%d]", set, currentBufferLength);
					out.write(buffer, set, currentBufferLength - set);
				}
				set = 0;
			} while (readBuffer());
		}

		void close() throws IOException {
			in.close();
		}
	}

	private static class RequestEdgeParser {
		final LittleEndianInputStream in;
		final LittleEndianOutputStream out;

		int firstField;

		public RequestEdgeParser(LittleEndianInputStream in, LittleEndianOutputStream out) throws IOException {
			this.in = in;
			this.out = out;

			// skip first request header and request id
			in.readInt();
			in.readInt();
		}

		void writeNextRequest(int requestId) throws IOException {
			out.writeInt(REQUEST_HEADER_TAG);
			out.writeInt(requestId);

			while (in.ready(0x10)) {
				firstField = in.readInt();
				if (firstField == REQUEST_HEADER_TAG) {
					in.readInt(); // skip request id
					break;
				} else {
					out.writeInt(firstField);
					out.writeInt(in.readInt());
				}
				out.writeInt(in.readInt());
				out.writeInt(in.readInt());
			}
		}

		void close() throws IOException {
			in.close();
		}
	}

	private static boolean isRequestTime(String previousSnippet) {
		Log.log("Checking for request time in snippet '%s'", previousSnippet);
		if (previousSnippet == null)
			return false;
		else
			return previousSnippet.substring(1).equals("<request-time> ");
	}

	private static boolean isRequestId(String previousSnippet) {
		Log.log("Checking for request id in snippet '%s'", previousSnippet);
		if (previousSnippet == null)
			return false;
		else
			return previousSnippet.substring(3).equals("<request-id> ");
	}

	private static final int REQUEST_HEADER_TAG = 2;

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
		File rightEdgeFile = rightDataSource.getRequestEdgeFile();

		Log.log("Is in place? %b for '%s' vs. '%s'", inPlaceMerge, rightDataSource.getRequestFile(),
				outputFiles.getRequestFile());

		if (inPlaceMerge) {
			rightRequestFile = new File(rightDataSource.getRequestFile().getParentFile(), rightDataSource
					.getRequestFile().getName() + ".tmp");
			rightEdgeFile = new File(rightDataSource.getRequestEdgeFile().getParentFile(), rightDataSource
					.getRequestEdgeFile().getName() + ".tmp");
			Log.log("Moving right request file to '%s' for in-place merge", rightRequestFile.getAbsolutePath());
			rightDataSource.getRequestFile().renameTo(rightRequestFile);
			rightDataSource.getRequestEdgeFile().renameTo(rightEdgeFile);
		}

		FileOutputStream requestOutput = new FileOutputStream(outputFiles.getRequestFile());
		RequestParser leftRequestParser = new RequestParser(new FileInputStream(leftDataSource.getRequestFile()),
				requestOutput);
		RequestParser rightRequestParser = new RequestParser(new FileInputStream(rightRequestFile), requestOutput);

		LittleEndianOutputStream requestEdgeOutput = new LittleEndianOutputStream(new FileOutputStream(
				outputFiles.getRequestEdgeFile(), true), "file:" + outputFiles.getRequestEdgeFile().getAbsolutePath());
		RequestEdgeParser leftEdgeParser = new RequestEdgeParser(new LittleEndianInputStream(
				leftDataSource.getRequestEdgeFile()), requestEdgeOutput);
		RequestEdgeParser rightEdgeParser = new RequestEdgeParser(new LittleEndianInputStream(rightEdgeFile),
				requestEdgeOutput);

		int nextRequestId = 0;
		leftRequestParser.readRequestStart();
		rightRequestParser.readRequestStart();
		do {
			if (rightRequestParser.eof
					|| (!leftRequestParser.eof && leftRequestParser.timestamp < rightRequestParser.timestamp)) {
				Log.log("Merge left request");
				leftRequestParser.writeNextRequest(nextRequestId);
				leftRequestParser.readRequestStart();
				leftEdgeParser.writeNextRequest(nextRequestId);
			} else {
				Log.log("Merge right request");
				rightRequestParser.writeNextRequest(nextRequestId);
				rightRequestParser.readRequestStart();
				rightEdgeParser.writeNextRequest(nextRequestId);
			}
			if (++nextRequestId == Integer.MAX_VALUE) {
				nextRequestId = 0;
				Log.log("Warning: request count exceeds maximum %d. Resetting to zero.", Integer.MAX_VALUE);
			}
		} while (!(leftRequestParser.eof && rightRequestParser.eof));

		leftRequestParser.close();
		rightRequestParser.close();
		requestOutput.flush();
		requestOutput.close();
		leftEdgeParser.close();
		rightEdgeParser.close();
		requestEdgeOutput.flush();
		requestEdgeOutput.close();
	}
}
