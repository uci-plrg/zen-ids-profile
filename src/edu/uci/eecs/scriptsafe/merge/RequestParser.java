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
	static final String REQUEST_TIME_TAG = "<request-time>";
	static final int REQUEST_TIME_TAG_LENGTH = REQUEST_TIME_TAG.length();

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
		if (fileRemaining <= 0) {
			eof = true;
			return false;
		}

		if (buffer == null)
			buffer = new byte[BUFFER_SIZE];
		else
			lastBufferTail = new String(buffer, BUFFER_SIZE - BUFFER_TAIL_SIZE, BUFFER_TAIL_SIZE);
		currentBufferLength = in.read(buffer);
		if (currentBufferLength <= 0) {
			eof = true;
			return false;
		}
		fileRemaining -= currentBufferLength;
		i = 0;
		return true;
	}

	private String getPreviousSnippet() {
		String previousSnippet = null;
		if (i < BUFFER_TAIL_SIZE) {
			if (lastBufferTail != null && lastBufferTail.length() > i) /* else not a match */
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
		int set = i, j = 0, k = 0;
		char timestampBuffer[] = new char[0x10];

		do {
			for (; i < currentBufferLength; i++) {
				if (j < REQUEST_TIME_TAG_LENGTH + 1/* space */) {
					j++;
				} else {
					if (buffer[i] == '\n') {
						timestamp = Long.parseLong(new String(timestampBuffer, 2, k - 2), 16);
						Log.message("Found request time at %d: 0x%x. Set is %d.", i, timestamp, set);
						requestStart.append(String.format("%s 0x%x", REQUEST_TIME_TAG, timestamp));
						return;
					} else {
						timestampBuffer[k++] = (char) buffer[i];
					}
				}
			}
			set = 0;
		} while (readBuffer());
	}

	void writeNextRequest(int requestId) throws IOException {
		Log.message("Writing request start of %d bytes", requestStart.length());
		out.write(requestStart.toString().getBytes(), 0, requestStart.length());
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
						out.write(String.format("%08d", requestId).getBytes(), 0, 8);
						out.write('\n');

						set = i + 11;
						if (set + 1 >= currentBufferLength && fileRemaining == 0) {
							Log.message("eof");
							eof = true;
						} else if (set > currentBufferLength) {
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
