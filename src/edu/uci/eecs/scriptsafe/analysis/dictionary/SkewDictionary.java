package edu.uci.eecs.scriptsafe.analysis.dictionary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.analysis.dictionary.DictionaryRequestHandler.Dictionary;
import edu.uci.eecs.scriptsafe.analysis.dictionary.DictionaryRequestHandler.Evaluation;
import edu.uci.eecs.scriptsafe.analysis.dictionary.DictionaryRequestHandler.WordInstance;

public class SkewDictionary implements Dictionary {

	private final Set<String> allWords = new HashSet<String>();
	private final Map<String, WordInstance> adminWords = new HashMap<String, WordInstance>();
	private final Map<String, WordInstance> anonymousWords = new HashMap<String, WordInstance>();

	private final RoutineLineMap routineLineMap;

	SkewDictionary(RoutineLineMap routineLineMap) {
		this.routineLineMap = routineLineMap;
	}

	@Override
	public Evaluation evaluateRoutine(int hash, boolean hasHint, boolean hintAdmin) {
		List<String> routineWords = routineLineMap.getWords(hash);

		int minCount = allWords.size() / 10;
		int adminCount, anonymousCount;
		int favorAdmin = 0, favorAnonymous = 0;
		WordInstance adminInstance, anonymousInstance;
		for (String word : routineWords) {
			adminInstance = adminWords.get(word);
			if (adminInstance == null)
				adminCount = 0;
			else
				adminCount = adminInstance.count;

			anonymousInstance = anonymousWords.get(word);
			if (anonymousInstance == null)
				anonymousCount = 0;
			else
				anonymousCount = anonymousInstance.count;

			if (anonymousCount > minCount && anonymousCount > (3 * adminCount))
				favorAnonymous++;
			else if (adminCount > minCount && adminCount > (3 * anonymousCount))
				favorAdmin++;
		}

		Evaluation evaluation = Evaluation.DUNNO;
		if (favorAdmin > favorAnonymous)
			evaluation = Evaluation.ADMIN;
		else if (favorAnonymous > favorAdmin)
			evaluation = Evaluation.ANONYMOUS;

		if (hasHint) {
			String correctness = evaluation.getCorrectnessString(hintAdmin);
			Log.log("Request for routine 0x%x (%d): %d admin vs. %d anonymous: %s (%s)", hash, routineWords.size(),
					favorAdmin, favorAnonymous, evaluation.toString().toLowerCase(), correctness);
		} else {
			Log.log("Request for routine 0x%x (%d): %d admin vs. %d anonymous: %s", hash, routineWords.size(),
					favorAdmin, favorAnonymous, evaluation.toString().toLowerCase());
		}

		return evaluation;
	}

	@Override
	public void addRoutine(int hash) {
		List<String> words = routineLineMap.getWords(hash, true);
		allWords.addAll(words);
		for (String word : words) {
			DictionaryRequestHandler.recordWordInstance(adminWords, word);
		}
		words = routineLineMap.getWords(hash, false);
		for (String word : words) {
			DictionaryRequestHandler.recordWordInstance(anonymousWords, word);
		}
	}

	@Override
	public void reportSummary() {
	}

	@Override
	public void reset() {
		allWords.clear();
		adminWords.clear();
		anonymousWords.clear();
	}
}
