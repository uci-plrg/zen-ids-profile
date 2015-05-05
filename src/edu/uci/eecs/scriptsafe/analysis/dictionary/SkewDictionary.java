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

		int minAdminMajority = (int) (8 * Math.log10(adminWords.size()));
		int minAnonymousMajority = (int) (8 * Math.log10(anonymousWords.size()));
		int maxAdminMinority = (int) (2 * Math.log10(adminWords.size()));
		int maxAnonymousMinority = (int) (2 * Math.log10(anonymousWords.size()));
		int adminCount, anonymousCount;
		float adminScore, anonymousScore, normalizer;
		int favorAdmin = 0, favorAnonymous = 0;
		WordInstance adminInstance, anonymousInstance;

		int adminOverMin = 0, anonymousOverMin = 0, adminSkew = 0, anonymousSkew = 0;

		if (adminWords.size() < 50 || anonymousWords.size() < 50)
			normalizer = 1f;
		else
			normalizer = (adminWords.size() / (float) anonymousWords.size());

		for (String word : routineWords) {
			adminInstance = adminWords.get(word);
			if (adminInstance == null) {
				adminCount = 0;
				adminScore = 0f;
			} else {
				adminCount = adminInstance.count;
				adminScore = adminInstance.count / normalizer;
			}

			anonymousInstance = anonymousWords.get(word);
			if (anonymousInstance == null) {
				anonymousCount = 0;
				anonymousScore = 0f;
			} else {
				anonymousCount = anonymousInstance.count;
				anonymousScore = anonymousInstance.count * normalizer;
			}

			if (anonymousCount > minAnonymousMajority)
				anonymousOverMin++;
			if (anonymousCount > (1.5 * adminCount))
				anonymousSkew++;
			if (adminCount > minAdminMajority)
				adminOverMin++;
			if (adminCount > (1.5 * anonymousCount))
				adminSkew++;

			if (anonymousCount > minAnonymousMajority || adminCount > minAdminMajority) {
				Log.message("\t%s: admin %d/%d %.2f, anonymous %d/%d %.2f", word, adminCount, minAdminMajority,
						adminScore, anonymousCount, minAnonymousMajority, anonymousScore);
			}

			if (anonymousCount > minAnonymousMajority && adminCount < maxAdminMinority
					&& anonymousScore > (3 * adminScore)) {
				favorAnonymous++;
				Log.log("\t%s: admin %d/%d %.2f, anonymous %d/%d %.2f", word, adminCount, minAdminMajority, adminScore,
						anonymousCount, minAnonymousMajority, anonymousScore);
			} else if (adminCount > minAdminMajority && anonymousCount < maxAnonymousMinority
					&& adminScore > (3 * anonymousScore)) {
				favorAdmin++;
				Log.log("\t%s: admin %d/%d %.2f, anonymous %d/%d %.2f", word, adminCount, minAdminMajority, adminScore,
						anonymousCount, minAnonymousMajority, anonymousScore);
			}
		}

		Evaluation evaluation = Evaluation.DUNNO;
		// report strict domination only
		if ((favorAdmin < 3 || favorAnonymous < 3) && (favorAdmin > 2 || favorAnonymous > 2)) {
			if (favorAdmin > favorAnonymous)
				evaluation = Evaluation.ADMIN;
			else if (favorAnonymous > favorAdmin)
				evaluation = Evaluation.ANONYMOUS;
		}

		if (hasHint) {
			String correctness = evaluation.getCorrectnessString(hintAdmin);
			Log.log("Request for routine 0x%x (%d): %d admin vs. %d anonymous: %s (%s) [%d,%d|%d,%d]", hash,
					routineWords.size(), favorAdmin, favorAnonymous, evaluation.toString().toLowerCase(), correctness,
					adminOverMin, adminSkew, anonymousOverMin, anonymousSkew);
		} else {
			Log.log("Request for routine 0x%x (%d): %d admin vs. %d anonymous: %s [%d,%d|%d,%d]", hash,
					routineWords.size(), favorAdmin, favorAnonymous, evaluation.toString().toLowerCase(), adminOverMin,
					adminSkew, anonymousOverMin, anonymousSkew);
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
		allWords.addAll(words);
		for (String word : words) {
			DictionaryRequestHandler.recordWordInstance(anonymousWords, word);
		}
	}

	@Override
	public void reportSummary() {
		Log.log("Total admin words: %d. Total anonymous words: %d.", adminWords.size(), anonymousWords.size());
	}

	@Override
	public void reset() {
		allWords.clear();
		adminWords.clear();
		anonymousWords.clear();
	}
}
