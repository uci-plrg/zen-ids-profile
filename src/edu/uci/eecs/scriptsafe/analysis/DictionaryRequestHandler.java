package edu.uci.eecs.scriptsafe.analysis;

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

class DictionaryRequestHandler {

	enum Instruction {
		GET_ADMIN_PROBABILITY((byte) 0),
		ADD_ROUTINE((byte) 1);

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

	private class WordInstance {
		final String word;
		int count = 1;

		WordInstance(String word) {
			this.word = word;
		}

	}

	private final RoutineLineMap routineLineMap;

	private final Map<String, WordInstance> adminWords = new HashMap<String, WordInstance>();
	private final Set<String> adminOnlyWords = new HashSet<String>();
	private final Map<String, WordInstance> anonymousWords = new HashMap<String, WordInstance>();
	private final Set<String> anonymousOnlyWords = new HashSet<String>();

	private int adminRoutineCount = 0;
	private int anonymousRoutineCount = 0;

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
				byte opcode = (byte) (data[0] & (byte) 0xf);
				boolean hasHint = (data[0] & 0xf0) != 0;
				boolean isAdmin = (data[0] & (byte) 0x80) != 0;
				instruction = Instruction.forByte(opcode);
				hash = ByteBuffer.wrap(data, 1, 4).getInt();

				switch (instruction) {
					case GET_ADMIN_PROBABILITY: {
						List<String> words = routineLineMap.getWords(hash);
						for (int i = words.size() - 1; i >= 0; i--) {
							if (isCommonWord(words.get(i)))
								words.remove(i);
						}

						testSet.clear();
						testSet.addAll(adminOnlyWords);
						testSet.retainAll(words);
						int adminOnlyWordCount = testSet.size();

						testSet.clear();
						testSet.addAll(anonymousOnlyWords);
						testSet.retainAll(words);
						int anonymousOnlyWordCount = testSet.size();

						if (hasHint) {
							boolean predictsNothing = adminOnlyWordCount == anonymousOnlyWordCount;
							boolean predictsAdmin = adminOnlyWordCount > anonymousOnlyWordCount;
							Log.log("Request for routine 0x%x: %d admin-only words, %d anonymous-only words: %d %s (%s)",
									hash, adminOnlyWordCount, anonymousOnlyWordCount, Math.abs(adminOnlyWordCount
											- anonymousOnlyWordCount), predictsNothing ? "dunno"
											: predictsAdmin ? "admin" : "anonymous", predictsNothing ? "dunno"
											: (isAdmin == predictsAdmin) ? "correct" : "wrong");
						} else {
							Log.log("Request for routine 0x%x: %d admin-only words, %d anonymous-only words: %d %s",
									hash, adminOnlyWordCount, anonymousOnlyWordCount, Math.abs(adminOnlyWordCount
											- anonymousOnlyWordCount),
									(adminOnlyWordCount == anonymousOnlyWordCount) ? "dunno"
											: (adminOnlyWordCount > anonymousOnlyWordCount) ? "admin" : "anonymous");
						}
					}
						break;
					case ADD_ROUTINE: {
						List<String> words = routineLineMap.getWords(hash, true);
						for (String word : words) {
							recordWordInstance(adminWords, word);
							anonymousOnlyWords.remove(word);
							if (!anonymousWords.containsKey(word))
								adminOnlyWords.add(word);
						}
						words = routineLineMap.getWords(hash, false);
						for (String word : words) {
							recordWordInstance(anonymousWords, word);
							adminOnlyWords.remove(word);
							if (!adminWords.containsKey(word))
								anonymousOnlyWords.add(word);
						}
						Log.log("Update routine 0x%x: %d total admin words, %d total anonymous words", hash,
								adminWords.size(), anonymousWords.size());
					}
						break;
				}
			} while (in.available() > 0);
		} finally {
			in.close();
		}
	}

	private boolean isCommonWord(String word) {
		WordInstance instance = adminWords.get(word);
		if (instance == null)
			return false;
		float adminFrequency = (instance.count / (float) adminWords.size());
		instance = anonymousWords.get(word);
		if (instance == null)
			return false;
		float anonymousFrequency = (instance.count / (float) anonymousWords.size());
		return adminFrequency > 0.2f && anonymousFrequency > 0.2f;
	}

	private void recordWordInstance(Map<String, WordInstance> words, String word) {
		WordInstance instance = words.get(word);
		if (instance == null) {
			instance = new WordInstance(word);
			words.put(word, instance);
		} else {
			instance.count++;
		}
	}
}
