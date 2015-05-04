package edu.uci.eecs.scriptsafe.analysis;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;

class DictionaryRequestHandler {

	enum Instruction {
		GET_ADMIN_WORD_RATIO((byte) 0),
		ADD_ADMIN_ROUTINE((byte) 1),
		GET_ANONYMOUS_WORD_RATIO((byte) 2),
		ADD_ANONYMOUS_ROUTINE((byte) 3);

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

	private final RoutineLineMap routineLineMap;

	private final Set<String> adminWords = new HashSet<String>();
	private final Set<String> anonymousWords = new HashSet<String>();

	DictionaryRequestHandler(RoutineLineMap routineLineMap) {
		this.routineLineMap = routineLineMap;
	}

	void respond(Socket request) throws IOException {
		int hash;
		byte data[];
		Instruction instruction;
		DataInputStream in = new DataInputStream(request.getInputStream());
		Set<String> testSet = new HashSet<String>();
		try {
			do {
				data = new byte[5];
				in.readFully(data);
				instruction = Instruction.forByte(data[0]);
				hash = ByteBuffer.wrap(data, 1, 4).getInt();

				switch (instruction) {
					case GET_ADMIN_WORD_RATIO: {
						List<String> words = routineLineMap.getWords(hash);
						testSet.clear();
						testSet.addAll(adminWords);
						testSet.retainAll(words);
						Log.log("Request for routine 0x%x: %d total words, %d overlap with admin words", hash,
								words.size(), testSet.size());
						if (words.size() > testSet.size()) {
							testSet.clear();
							testSet.addAll(words);
							testSet.removeAll(adminWords);
							Log.log("\tNon-admin words: %s", testSet);
						}
					}
						break;
					case ADD_ADMIN_ROUTINE: {
						List<String> words = routineLineMap.getWords(hash);
						adminWords.addAll(words);
						Log.log("Update admin routine 0x%x: %d total admin words", hash, adminWords.size());
					}
						break;
					case GET_ANONYMOUS_WORD_RATIO: {
						List<String> words = routineLineMap.getWords(hash);
						testSet.clear();
						testSet.addAll(anonymousWords);
						testSet.retainAll(words);
						Log.log("Request for routine 0x%x: %d total words, %d overlap with anonymous words", hash,
								words.size(), testSet.size());
						if (words.size() > testSet.size()) {
							testSet.clear();
							testSet.addAll(words);
							testSet.removeAll(anonymousWords);
							Log.log("\tNon-anonymous words: %s", testSet);
						}
					}
						break;
					case ADD_ANONYMOUS_ROUTINE: {
						List<String> words = routineLineMap.getWords(hash);
						anonymousWords.addAll(words);
						Log.log("Update anonymous routine 0x%x: %d total anonymous words", hash, anonymousWords.size());
					}
						break;
				}
			} while (in.available() > 0);
		} finally {
			in.close();
		}
	}
}
