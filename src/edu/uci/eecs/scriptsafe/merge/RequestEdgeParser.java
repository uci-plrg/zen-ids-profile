package edu.uci.eecs.scriptsafe.merge;

import java.io.File;
import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.io.LittleEndianOutputStream;

public class RequestEdgeParser {

	private static final int REQUEST_HEADER_TAG = 3;

	final LittleEndianInputStream in;
	final LittleEndianOutputStream out;

	int firstField;

	public RequestEdgeParser(File inputFile, File outputFile) throws IOException {
		this(new LittleEndianInputStream(inputFile), new LittleEndianOutputStream(outputFile));
	}

	public RequestEdgeParser(LittleEndianInputStream in, LittleEndianOutputStream out) throws IOException {
		this.in = in;
		this.out = out;

		// skip first request header and request id
		in.readInt();
		in.readInt();
		in.readInt();
		in.readInt();
	}

	void writeNextRequest(int requestId) throws IOException {
		out.writeInt(REQUEST_HEADER_TAG);
		out.writeInt(requestId);
		out.writeInt(0);
		out.writeInt(0);

		while (in.ready(0x10)) {
			firstField = in.readInt();
			if (firstField == REQUEST_HEADER_TAG) {
				in.readInt(); // skip request id
				in.readInt();
				in.readInt();
				break;
			} else {
				out.writeInt(firstField);
				out.writeInt(in.readInt());
				out.writeInt(in.readInt());
				out.writeInt(in.readInt());
			}
		}
	}

	void close() throws IOException {
		in.close();
	}
}
