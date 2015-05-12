package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.scriptsafe.analysis.dictionary.Dictionary.Evaluation;

class SourceWordList implements FeatureResponseGenerator.Field {

	private class Loader {
		final Set<String> allWords = new HashSet<String>();
		final Map<String, WordInstance> adminWords = new HashMap<String, WordInstance>();
		final Map<String, WordInstance> anonymousWords = new HashMap<String, WordInstance>();

		int minAdminMajority = 1;
		int minAnonymousMajority = 1;

		void loadRoutineWords() {
			for (Map.Entry<Integer, Integer> entry : dataSource.requestGraph.calledRoutineUserLevel.entrySet()) {
				boolean isEmpty = true;
				List<String> words = dataSource.routineLineMap.getWords(entry.getKey(), true);
				isEmpty &= words.isEmpty();
				allWords.addAll(words);
				for (String word : words)
					recordWordInstance(adminWords, word);
				words = dataSource.routineLineMap.getWords(entry.getKey(), false);
				isEmpty &= words.isEmpty();
				allWords.addAll(words);
				for (String word : words)
					recordWordInstance(anonymousWords, word);
				if (!isEmpty) {
					if (entry.getValue() < 2)
						anonymousRoutineCount++;
					else
						adminRoutineCount++;
				}
			}
		}

		void identifyPredictors() {
			for (WordInstance adminWord : adminWords.values()) {
				WordInstance anonymousWord = anonymousWords.get(adminWord.word);
				if (adminWord.count < minAdminMajority
						&& (anonymousWord == null || anonymousWord.count < minAnonymousMajority))
					continue; // skip if both below the threshold

				predictors.put(adminWord.word, new Predictor(adminWord, anonymousWord));
			}
			for (WordInstance anonymousWord : anonymousWords.values()) {
				if (predictors.containsKey(anonymousWord.word))
					continue;
				WordInstance adminWord = adminWords.get(anonymousWord.word);
				if (anonymousWord.count < minAnonymousMajority
						&& (adminWord == null || adminWord.count < minAdminMajority))
					continue; // skip if both below the threshold

				predictors.put(adminWord.word, new Predictor(adminWord, anonymousWord));
			}
		}

		void calculateMajorityThresholds() {
			minAdminMajority = (int) (majorityThresholdFactor * Math.log10(adminRoutineCount));
			minAnonymousMajority = (int) (majorityThresholdFactor * Math.log10(anonymousRoutineCount));
		}
	}

	private class Predictor implements FeatureRoleCountElement {
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
				adminProbability = (adminWord.count / (float) adminRoutineCount) * 100f;
				adminCount = adminWord.count;
			}
			if (anonymousWord == null) {
				anonymousProbability = 0f;
				evaluation = Evaluation.ADMIN;
				skew = 1f;
				anonymousCount = 0;
			} else {
				anonymousProbability = (anonymousWord.count / (float) anonymousRoutineCount) * 100f;
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
		public int getAdminCount() {
			return adminCount;
		}

		@Override
		public int getAnonymousCount() {
			return anonymousCount;
		}
	}

	private static class WordInstance {
		final String word;
		int count = 1;

		WordInstance(String word) {
			this.word = word;
		}
	}

	private FeatureDataSource dataSource;

	private final Loader loader = new Loader();

	private final Map<String, Predictor> predictors = new HashMap<String, Predictor>();

	private final Set<Predictor> routinePredictors = new HashSet<Predictor>();
	private final FeatureRoleCounts routinePredictorCounts = new FeatureRoleCounts();

	/* Configurable */
	private final float skewThreshold;
	private final float majorityThresholdFactor;

	private int adminRoutineCount = 0;
	private int anonymousRoutineCount = 0;

	SourceWordList(float skewThreshold, float majorityThresholdFactor) {
		this.skewThreshold = skewThreshold;
		this.majorityThresholdFactor = majorityThresholdFactor;
	}

	void setDataSource(FeatureDataSource dataSource) {
		this.dataSource = dataSource;

		loader.loadRoutineWords();
		loader.identifyPredictors();
	}

	void evaluateRoutine(int routineHash) {
		List<String> words = dataSource.routineLineMap.getWords(routineHash);
		for (String word : words) {
			Predictor predictor = predictors.get(word);
			if (predictor != null && predictor.skew > skewThreshold)
				routinePredictors.add(predictor);
		}
	}

	@Override
	public int getByteCount() {
		return routinePredictorCounts.getByteCount() * routinePredictors.size();
	}

	@Override
	public void write(ByteBuffer buffer) {
		for (Predictor predictor : routinePredictors) {
			routinePredictorCounts.reset();
			routinePredictorCounts.addCounts(predictor);
			routinePredictorCounts.write(buffer);
		}
	}

	@Override
	public void reset() {
		routinePredictors.clear();
	}

	void recordWordInstance(Map<String, WordInstance> words, String word) {
		WordInstance instance = words.get(word);
		if (instance == null) {
			instance = new WordInstance(word);
			words.put(word, instance);
		} else {
			instance.count++;
		}
	}
}
