package edu.uci.eecs.scriptsafe.merge;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.log.Log;

class PersistenceParser {

	final FileInputStream in;
	final FileOutputStream out;

	int fileRemaining;
	boolean atNewline = false;

	public PersistenceParser(FileInputStream in, FileOutputStream out) throws IOException {
		this.in = in;
		this.out = out;

		in.read(); // skip first '@'
		fileRemaining = in.available();
	}

	void writeNextRequest() throws IOException {
		out.write('@');
		while (fileRemaining-- > 0) {
			int c = in.read();
			if (atNewline && c == '@')
				break;
			out.write(c);
			atNewline = (c == '\n');
		}
	}

	void close() throws IOException {
		in.close();
	}
}
