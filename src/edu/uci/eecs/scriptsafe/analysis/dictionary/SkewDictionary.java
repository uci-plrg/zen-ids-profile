package edu.uci.eecs.scriptsafe.analysis.dictionary;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.analysis.dictionary.DictionaryRequestHandler.WordInstance;

public class SkewDictionary implements Dictionary {

	private class EvaluationStatistics {
		boolean isStale = true;

		int minAdminMajority;
		int minAnonymousMajority;

		int adminTotalInstances;
		float adminAverageInstances;
		int anonymousTotalInstances;
		float anonymousAverageInstances;

		int adminRoutineCount = 0, anonymousRoutineCount = 0;

		float normalizer;

		void update() {
			if (!isStale)
				return;

			minAdminMajority = 1; // (int) (4 * Math.log10(adminRoutineCount));
			minAnonymousMajority = 1; // (int) (4 * Math.log10(anonymousRoutineCount));

			adminTotalInstances = 0;
			for (WordInstance i : adminWords.values()) {
				adminTotalInstances += i.count;
			}
			adminAverageInstances = adminTotalInstances / (float) adminWords.size();

			anonymousTotalInstances = 0;
			for (WordInstance i : anonymousWords.values()) {
				anonymousTotalInstances += i.count;
			}
			anonymousAverageInstances = anonymousTotalInstances / (float) anonymousWords.size();

			if (adminWords.size() < 50 || anonymousWords.size() < 50)
				normalizer = 1f;
			else
				normalizer = (anonymousTotalInstances / (float) adminTotalInstances);

			Log.log("Updated normalizer to favor admin by %.3f", normalizer);

			isStale = false;
		}

	}

	private static final float SKEW_FACTOR = 4f;

	private final Set<String> allWords = new HashSet<String>();
	private final Map<String, WordInstance> adminWords = new HashMap<String, WordInstance>();
	private final Map<String, WordInstance> anonymousWords = new HashMap<String, WordInstance>();
	private final EvaluationStatistics stats = new EvaluationStatistics();

	private final RoutineLineMap routineLineMap;

	public SkewDictionary(RoutineLineMap routineLineMap) {
		this.routineLineMap = routineLineMap;
	}

	@Override
	public Evaluation evaluateRoutine(int hash, boolean hasHint, boolean hintAdmin) {
		List<String> routineWords = routineLineMap.getWords(hash);
		stats.update();

		int adminCount, anonymousCount;
		float adminScore, anonymousScore;
		int favorAdmin = 0, favorAnonymous = 0;
		WordInstance adminInstance, anonymousInstance;

		int adminOverMin = 0, anonymousOverMin = 0, adminSkew = 0, anonymousSkew = 0;

		for (String word : routineWords) {
			adminInstance = adminWords.get(word);
			if (adminInstance == null) {
				adminCount = 0;
				adminScore = 0f;
			} else {
				adminCount = adminInstance.count;
				// adminScore = adminInstance.count * stats.normalizer;
				adminScore = (adminInstance.count / (float) stats.adminRoutineCount) * 100f;
			}

			anonymousInstance = anonymousWords.get(word);
			if (anonymousInstance == null) {
				anonymousCount = 0;
				anonymousScore = 0f;
			} else {
				anonymousCount = anonymousInstance.count;
				// anonymousScore = anonymousInstance.count / stats.normalizer;
				anonymousScore = (anonymousInstance.count / (float) stats.anonymousRoutineCount) * 100f;
			}

			if (anonymousCount > stats.minAnonymousMajority)
				anonymousOverMin++;
			if (anonymousCount > (SKEW_FACTOR * adminCount))
				anonymousSkew++;
			if (adminCount > stats.minAdminMajority)
				adminOverMin++;
			if (adminCount > (SKEW_FACTOR * anonymousCount))
				adminSkew++;

			if ((anonymousCount > stats.minAnonymousMajority) || (adminCount > stats.minAdminMajority)) {
				Log.message("\t%s: admin %d/%d %.2f, anonymous %d/%d %.2f", word, adminCount, stats.minAdminMajority,
						adminScore, anonymousCount, stats.minAnonymousMajority, anonymousScore);
			}

			if (anonymousCount > stats.minAnonymousMajority && anonymousScore > (SKEW_FACTOR * adminScore)) {
				favorAnonymous++;
				Log.message("\t%s: admin %d/%d %.2f, anonymous %d/%d %.2f [anonymous %.3f]", word, adminCount,
						stats.minAdminMajority, adminScore, anonymousCount, stats.minAnonymousMajority, anonymousScore,
						anonymousScore / (anonymousScore + adminScore));
			} else if (adminCount > stats.minAdminMajority && adminScore > (SKEW_FACTOR * anonymousScore)) {
				favorAdmin++;
				Log.message("\t%s: admin %d/%d %.2f, anonymous %d/%d %.2f [admin %.3f]", word, adminCount,
						stats.minAdminMajority, adminScore, anonymousCount, stats.minAnonymousMajority, anonymousScore,
						adminScore / (anonymousScore + adminScore));
			}
		}

		Evaluation evaluation = Evaluation.DUNNO;
		// report strict domination only
		if (Math.abs(favorAdmin - favorAnonymous) > 5) {
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
	public void addRoutine(int hash, boolean isAdmin) {
		boolean isEmpty = true;
		List<String> words = routineLineMap.getWords(hash, true);
		isEmpty &= words.isEmpty();
		allWords.addAll(words);
		for (String word : words) {
			DictionaryRequestHandler.recordWordInstance(adminWords, word);
		}
		words = routineLineMap.getWords(hash, false);
		isEmpty &= words.isEmpty();
		allWords.addAll(words);
		for (String word : words) {
			DictionaryRequestHandler.recordWordInstance(anonymousWords, word);
		}
		if (!isEmpty) {
			if (isAdmin)
				stats.adminRoutineCount++;
			else
				stats.anonymousRoutineCount++;
		}
		stats.isStale = true;
	}

	private class Predictor {
		float adminProbability;
		float anonymousProbability;
		float skewBase;
		Evaluation evaluation;
		String word;
		float skew;
		int adminCount;
		int anonymousCount;

		Predictor(WordInstance adminWord, WordInstance anonymousWord) {
			if (adminWord == null) {
				adminProbability = 0f;
				evaluation = Evaluation.ANONYMOUS;
				skew = 1f;
				adminCount = 0;
			} else {
				adminProbability = (adminWord.count / (float) stats.adminRoutineCount) * 100f;
				adminCount = adminWord.count;
			}
			if (anonymousWord == null) {
				anonymousProbability = 0f;
				evaluation = Evaluation.ADMIN;
				skew = 1f;
				anonymousCount = 0;
			} else {
				anonymousProbability = (anonymousWord.count / (float) stats.anonymousRoutineCount) * 100f;
				anonymousCount = anonymousWord.count;
			}
			skewBase = adminProbability + anonymousProbability;
			word = (adminWord == null) ? anonymousWord.word : adminWord.word;
			if (adminWord != null && anonymousWord != null) {
				if (adminProbability > anonymousProbability) {
					evaluation = Evaluation.ADMIN;
					skew = adminProbability / skewBase;
				} else {
					evaluation = Evaluation.ANONYMOUS;
					skew = anonymousProbability / skewBase;
				}
			}
		}

		@Override
		public int hashCode() {
			return word.hashCode();
		}

		@Override
		public boolean equals(Object o) {
			return (o instanceof Predictor && ((Predictor) o).word.equals(word));
		}
	}

	private class PredictorSorter implements Comparator<Predictor> {
		@Override
		public int compare(Predictor first, Predictor second) {
			int comparison = (int) Math.signum(second.skew - first.skew);
			if (comparison != 0) {
				return comparison;
			} else {
				float firstProbability = (first.evaluation == Evaluation.ADMIN) ? first.adminProbability
						: first.anonymousProbability;
				float secondProbability = (second.evaluation == Evaluation.ADMIN) ? second.adminProbability
						: second.anonymousProbability;
				comparison = (int) Math.signum(secondProbability - firstProbability);
				if (comparison != 0)
					return comparison;
				else
					return first.word.compareTo(second.word);
			}
		}
	}

	@Override
	public void reportSummary(int predictorCount) {
		Log.log("Admin words: %d total, %d instances, %.2f avg. Anonymous words: %d total, %d instances, %.2f avg.",
				adminWords.size(), stats.adminTotalInstances, stats.adminAverageInstances, anonymousWords.size(),
				stats.anonymousTotalInstances, stats.anonymousAverageInstances);

		Set<Predictor> predictors = new TreeSet<Predictor>(new PredictorSorter());
		for (WordInstance adminWord : adminWords.values()) {
			WordInstance anonymousWord = anonymousWords.get(adminWord.word);
			if (adminWord.count < stats.minAdminMajority
					&& (anonymousWord == null || anonymousWord.count < stats.minAnonymousMajority))
				continue; // skip if both below the threshold

			predictors.add(new Predictor(adminWord, anonymousWord));
		}
		for (WordInstance anonymousWord : anonymousWords.values()) {
			WordInstance adminWord = adminWords.get(anonymousWord.word);
			if (anonymousWord.count < stats.minAnonymousMajority
					&& (adminWord == null || adminWord.count < stats.minAdminMajority))
				continue; // skip if both below the threshold

			predictors.add(new Predictor(adminWord, anonymousWord));
		}

		int limit = (predictorCount == -1) ? Integer.MAX_VALUE : predictorCount;
		Iterator<Predictor> p = predictors.iterator();
		for (int i = 0; i < limit && p.hasNext(); i++) {
			Predictor predictor = p.next();
			Log.log("\t%20s: %.3f %10s:  %02.3f(%02d) admin, %02.3f(%02d) anonymous)", predictor.word, predictor.skew,
					predictor.evaluation.toString().toLowerCase(), predictor.adminProbability, predictor.adminCount,
					predictor.anonymousProbability, predictor.anonymousCount);
		}
	}

	@Override
	public void reset() {
		allWords.clear();
		adminWords.clear();
		anonymousWords.clear();
	}
}
