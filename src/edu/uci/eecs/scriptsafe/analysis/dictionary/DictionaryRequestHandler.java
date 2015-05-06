package edu.uci.eecs.scriptsafe.analysis.dictionary;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.analysis.AnalysisException;

class DictionaryRequestHandler {

	enum Instruction {
		GET_ADMIN_PROBABILITY((byte) 0),
		ADD_ROUTINE((byte) 1),
		REPORT_SUMMARY((byte) 2),
		RESET((byte) 3);

		final byte opcode;

		private Instruction(byte opcode) {
			this.opcode = opcode;
		}

		static Instruction forByte(byte b) {
			for (Instruction i : values()) {
				if (i.opcode == b)
					return i;
			}
			throw new AnalysisException("Cannot understand instruction with opcode %d", b);
		}

		static byte[] create(Instruction instruction, int hash) {
			byte instructionBytes[] = new byte[5];
			instructionBytes[0] = instruction.opcode;
			byte hashBytes[] = ByteBuffer.allocate(4).putInt(hash).array();
			for (int i = 0; i < 4; i++)
				instructionBytes[i + 1] = hashBytes[i];
			return instructionBytes;
		}
	}

	static class WordInstance {
		final String word;
		int count = 1;

		WordInstance(String word) {
			this.word = word;
		}
	}

	enum Evaluation {
		ADMIN,
		ANONYMOUS,
		DUNNO;

		String getCorrectnessString(boolean hintAdmin) {
			String correctness = "shrug";
			if (this != DUNNO) {
				if ((this == ADMIN && hintAdmin) || (this == ANONYMOUS && !hintAdmin))
					correctness = "correct";
				else
					correctness = "wrong";
			}
			return correctness;
		}
	}

	interface Dictionary {
		Evaluation evaluateRoutine(int hash, boolean hasHint, boolean hintAdmin);

		void addRoutine(int hash, boolean isAdmin);

		void reportSummary();

		void reset();
	}

	static void recordWordInstance(Map<String, WordInstance> words, String word) {
		WordInstance instance = words.get(word);
		if (instance == null) {
			instance = new WordInstance(word);
			words.put(word, instance);
		} else {
			instance.count++;
		}
	}

	private final RoutineLineMap routineLineMap;

	private final Dictionary dictionary;

	DictionaryRequestHandler(RoutineLineMap routineLineMap) {
		this.routineLineMap = routineLineMap;
		this.dictionary = new SkewDictionary(routineLineMap);
	}

	void respond(Socket request) throws IOException {
		byte data[];
		DataInputStream in = new DataInputStream(request.getInputStream());
		try {
			do {
				data = new byte[5];
				in.readFully(data);
				execute(data);
			} while (in.available() > 0);
		} finally {
			in.close();
		}
	}

	void execute(byte data[]) {
		byte opcode = (byte) (data[0] & (byte) 0xf);
		boolean hasHint = (data[0] & 0xf0) != 0;
		boolean hintAdmin = (data[0] & (byte) 0x80) != 0;
		Instruction instruction = Instruction.forByte(opcode);
		int hash = ByteBuffer.wrap(data, 1, 4).getInt();

		switch (instruction) {
			case GET_ADMIN_PROBABILITY:
				dictionary.evaluateRoutine(hash, hasHint, hintAdmin);
				break;
			case ADD_ROUTINE:
				dictionary.addRoutine(hash, hintAdmin);
				break;
			case REPORT_SUMMARY:
				dictionary.reportSummary();
				break;
			case RESET:
				dictionary.reset();
				break;
		}
	}
}
