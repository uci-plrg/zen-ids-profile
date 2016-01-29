package edu.uci.eecs.scriptsafe.merge;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.log.Log;

class RequestParser {

	private static boolean isRequestTime(String previousSnippet) {
		Log.message("Checking for request time in snippet '%s'", previousSnippet);
		if (previousSnippet == null)
			return false;
		else
			return previousSnippet.substring(1).equals("<request-time> ");
	}

	private static boolean isRequestId(String previousSnippet) {
		Log.message("Checking for request id in snippet '%s'", previousSnippet);
		if (previousSnippet == null)
			return false;
		else
			return previousSnippet.substring(3).equals("<request-id> ");
	}

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

	private boolean readBuffer() throws IOException {
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

	private String getPreviousSnippet() {
		String previousSnippet = null;
		if (i < BUFFER_TAIL_SIZE) {
			if (lastBufferTail != null) /* else not a match */
				previousSnippet = lastBufferTail.substring(i) + new String(buffer, 0, i);
		} else {
			previousSnippet = new String(buffer, i - BUFFER_TAIL_SIZE, BUFFER_TAIL_SIZE);
		}
		return previousSnippet;
	}

	private String readString(int readLength) throws IOException {
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

	private String readString(char delimiter) throws IOException {
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
					Log.message("Found request time at %d: 0x%x. Set is %d.", i, timestamp, set);
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
		Log.message("Writing request start of %d bytes", requestStart.length());
		out.write(requestStart.toString().getBytes());
		requestStart.setLength(0);

		do {
			int set = i;
			for (; i < currentBufferLength; i++) {
				if (buffer[i] == '|') {
					if (isRequestId(getPreviousSnippet())) {
						Log.message("Found request id at %d. Set is at %d, buffer has %d bytes.", i, set,
								currentBufferLength);
						Log.message("Writing [%d,%d] plus request id", set, i + 1);
						out.write(buffer, set, (i - set) + 1);
						out.write(String.format("%08d", requestId).getBytes());

						set = i + 9;
						if (set + 1 >= currentBufferLength && fileRemaining == 0) {
							Log.message("eof");
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
				Log.message("Writing [%d,%d]", set, currentBufferLength);
				out.write(buffer, set, currentBufferLength - set);
			}
			set = 0;
		} while (readBuffer());
	}

	void close() throws IOException {
		in.close();
	}
}
