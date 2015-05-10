package edu.uci.eecs.scriptsafe.analysis.dictionary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.analysis.dictionary.DictionaryRequestHandler.Dictionary;
import edu.uci.eecs.scriptsafe.analysis.dictionary.DictionaryRequestHandler.Evaluation;
import edu.uci.eecs.scriptsafe.analysis.dictionary.DictionaryRequestHandler.WordInstance;

class ExclusiveDictionary implements Dictionary {

	private final Map<String, WordInstance> adminWords = new HashMap<String, WordInstance>();
	private final Set<String> adminOnlyWords = new HashSet<String>();
	private final Map<String, WordInstance> anonymousWords = new HashMap<String, WordInstance>();
	private final Set<String> anonymousOnlyWords = new HashSet<String>();

	private final Set<String> testSet = new HashSet<String>();

	private final RoutineLineMap routineLineMap;

	ExclusiveDictionary(RoutineLineMap routineLineMap) {
		this.routineLineMap = routineLineMap;
	}

	@Override
	public Evaluation evaluateRoutine(int hash, boolean hasHint, boolean hintAdmin) {
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

		Evaluation evaluation = Evaluation.DUNNO;
		if (adminOnlyWordCount < anonymousOnlyWordCount)
			evaluation = Evaluation.ANONYMOUS;
		else if (adminOnlyWordCount > anonymousOnlyWordCount)
			evaluation = Evaluation.ADMIN;

		if (hasHint) {
			String correctness = evaluation.getCorrectnessString(hintAdmin);
			Log.log("Request for routine 0x%x: %d admin-only words, %d anonymous-only words: %d %s (%s)", hash,
					adminOnlyWordCount, anonymousOnlyWordCount, Math.abs(adminOnlyWordCount - anonymousOnlyWordCount),
					evaluation.toString().toLowerCase(), correctness);
		} else {
			Log.log("Request for routine 0x%x: %d admin-only words, %d anonymous-only words: %d %s", hash,
					adminOnlyWordCount, anonymousOnlyWordCount, Math.abs(adminOnlyWordCount - anonymousOnlyWordCount),
					evaluation.toString().toLowerCase());
		}
		return evaluation;
	}

	@Override
	public void addRoutine(int hash, boolean isAdmin) {
		List<String> words = routineLineMap.getWords(hash, true);
		for (String word : words) {
			DictionaryRequestHandler.recordWordInstance(adminWords, word);
			anonymousOnlyWords.remove(word);
			if (!anonymousWords.containsKey(word))
				adminOnlyWords.add(word);
		}
		words = routineLineMap.getWords(hash, false);
		for (String word : words) {
			DictionaryRequestHandler.recordWordInstance(anonymousWords, word);
			adminOnlyWords.remove(word);
			if (!adminWords.containsKey(word))
				anonymousOnlyWords.add(word);
		}
		Log.log("Update routine 0x%x: %d admin-only words, %d anonymous-only words", hash, adminOnlyWords.size(),
				anonymousOnlyWords.size());
	}

	@Override
	public void reportSummary(int predictorCount) {
		if (predictorCount > 0) {
			int limit = (predictorCount == -1) ? Integer.MAX_VALUE : predictorCount;
			Log.log("Admin-only words:");
			Iterator<String> w = adminOnlyWords.iterator();
			for (int i = 0; i < limit && w.hasNext(); i++)
				Log.log("\t%s", w.next());
			Log.log("Anonymous-only words:");
			w = anonymousOnlyWords.iterator();
			for (int i = 0; i < limit && w.hasNext(); i++)
				Log.log("\t%s", w.next());
		}
	}

	@Override
	public void reset() {
		adminWords.clear();
		adminOnlyWords.clear();
		anonymousWords.clear();
		anonymousOnlyWords.clear();
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
}
