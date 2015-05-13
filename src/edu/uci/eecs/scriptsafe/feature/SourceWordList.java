package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.analysis.dictionary.Dictionary.Evaluation;
import edu.uci.eecs.scriptsafe.analysis.dictionary.WordAppearanceCount;

class SourceWordList {

	private enum ConfigOptions {
		SKEW_THRESHOLD(0.9f),
		MAJORITY_THRESHOLD_FACTOR(4f);

		final float defaultValue;

		private ConfigOptions(float defaultValue) {
			this.defaultValue = defaultValue;
		}

		float getProperty(Properties config) {
			float value = defaultValue;
			String propertyValue = config.getProperty(toString());
			if (propertyValue != null) {
				try {
					value = Float.parseFloat(propertyValue);
				} catch (NumberFormatException e) {
					Log.error("%s attempting to parse '%s' as a float", e.getClass().getSimpleName());
					value = defaultValue;
				}
			}
			return value;
		}
	}

	private class Loader {
		final Map<String, WordInstance> allWords = new HashMap<String, WordInstance>();
		final Map<String, WordInstance> adminWords = new HashMap<String, WordInstance>();
		final Map<String, WordInstance> anonymousWords = new HashMap<String, WordInstance>();

		int minAdminMajority = 1;
		int minAnonymousMajority = 1;

		void loadRoutineWords() {
			adminRoutineCount = anonymousRoutineCount = 0;

			// aggregate counts (using WordSetBuilder?)
			for (Map.Entry<Integer, Integer> entry : dataSource.trainingRequestGraph.calledRoutineUserLevel.entrySet()) {
				boolean isEmpty = true;
				List<WordAppearanceCount> words = dataSource.routineLineMap.getWords(entry.getKey(), true);
				isEmpty &= words.isEmpty();
				for (WordAppearanceCount word : words) {
					recordWordInstance(adminWords, word);
					recordWordInstance(allWords, word);
				}
				words = dataSource.routineLineMap.getWords(entry.getKey(), false);
				isEmpty &= words.isEmpty();
				for (WordAppearanceCount word : words) {
					recordWordInstance(anonymousWords, word);
					recordWordInstance(allWords, word);
				}
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
				if (adminWord.routineMatchCount < minAdminMajority
						&& (anonymousWord == null || anonymousWord.routineMatchCount < minAnonymousMajority))
					continue; // skip if both below the threshold

				predictors.put(adminWord.word, new Predictor(adminWord, anonymousWord));
			}
			for (WordInstance anonymousWord : anonymousWords.values()) {
				if (predictors.containsKey(anonymousWord.word))
					continue;
				WordInstance adminWord = adminWords.get(anonymousWord.word);
				if (anonymousWord.routineMatchCount < minAnonymousMajority
						&& (adminWord == null || adminWord.routineMatchCount < minAdminMajority))
					continue; // skip if both below the threshold

				predictors.put(anonymousWord.word, new Predictor(adminWord, anonymousWord));
			}
		}

		void calculateMajorityThresholds() {
			minAdminMajority = (int) (majorityThresholdFactor * Math.log10(adminRoutineCount));
			minAnonymousMajority = (int) (majorityThresholdFactor * Math.log10(anonymousRoutineCount));
		}

		void recordWordInstance(Map<String, WordInstance> words, WordAppearanceCount word) {
			WordInstance instance = words.get(word.word);
			if (instance == null) {
				instance = new WordInstance(word.word);
				words.put(word.word, instance);
			} else {
				instance.routineMatchCount++;
			}
			instance.appearanceCount += word.getCount();
		}
	}

	private class Predictor implements FeatureRoleCountElement {
		float adminProbability;
		float anonymousProbability;
		float skewBase;
		Evaluation evaluation;
		String word;
		float skew;
		final WordInstance adminWord;
		final WordInstance anonymousWord;

		Predictor(WordInstance adminWord, WordInstance anonymousWord) {
			this.adminWord = adminWord;
			this.anonymousWord = anonymousWord;

			if (adminWord == null) {
				adminProbability = 0f;
				evaluation = Evaluation.ANONYMOUS;
				skew = 1f;
			} else {
				adminProbability = (adminWord.routineMatchCount / (float) adminRoutineCount) * 100f;
			}
			if (anonymousWord == null) {
				anonymousProbability = 0f;
				evaluation = Evaluation.ADMIN;
				skew = 1f;
			} else {
				anonymousProbability = (anonymousWord.routineMatchCount / (float) anonymousRoutineCount) * 100f;
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
			return adminWord == null ? 0 : adminWord.routineMatchCount;
		}

		@Override
		public int getAnonymousCount() {
			return anonymousWord == null ? 0 : anonymousWord.routineMatchCount;
		}
	}

	private static class WordInstance {
		final String word;
		int appearanceCount = 0;
		int routineMatchCount = 1;

		WordInstance(String word) {
			this.word = word;
		}
	}

	private class WordMatchResponseField implements FeatureResponseGenerator.Field {
		@Override
		public int getByteCount() {
			return routineWords.size() * 20; /* 20 = 4 bytes * 5 fields */
		}

		@Override
		public void write(ByteBuffer buffer) {
			for (WordAppearanceCount word : routineWords) {
				Predictor predictor = predictors.get(word.word);
				if (predictor != null) {
					buffer.putInt(word.getCount());
					buffer.putInt(predictor.adminWord == null ? 0 : predictor.adminWord.routineMatchCount);
					buffer.putInt(predictor.anonymousWord == null ? 0 : predictor.anonymousWord.routineMatchCount);
					buffer.putInt(predictor.adminWord == null ? 0 : predictor.adminWord.appearanceCount);
					buffer.putInt(predictor.anonymousWord == null ? 0 : predictor.anonymousWord.appearanceCount);
				}
			}
		}

		@Override
		public void reset() {
			routineWords.clear();
		}
	}

	private class NonEmptyRoutineResponseField implements FeatureResponseGenerator.Field {
		@Override
		public int getByteCount() {
			return 8;
		}

		@Override
		public void write(ByteBuffer buffer) {
			buffer.putInt(adminRoutineCount);
			buffer.putInt(anonymousRoutineCount);
		}

		@Override
		public void reset() {
		}
	}

	private final FeatureDataSource dataSource;

	private final Loader loader = new Loader();

	private final Map<String, Predictor> predictors = new HashMap<String, Predictor>();

	/* Transitory per FeatureResponseGenerator.Field workflow */
	private final List<WordAppearanceCount> routineWords = new ArrayList<WordAppearanceCount>();

	/* Configurable */
	private final float skewThreshold;
	private final float majorityThresholdFactor;

	private int adminRoutineCount;
	private int anonymousRoutineCount;

	SourceWordList(Properties config, FeatureDataSource dataSource) {
		this.dataSource = dataSource;

		this.skewThreshold = ConfigOptions.SKEW_THRESHOLD.getProperty(config);
		this.majorityThresholdFactor = ConfigOptions.MAJORITY_THRESHOLD_FACTOR.getProperty(config);
	}

	void reload() {
		loader.loadRoutineWords();
		loader.identifyPredictors();
	}

	void evaluateRoutine(int routineHash) {
		routineWords.addAll(dataSource.routineLineMap.getWords(routineHash));
		for (int i = routineWords.size() - 1; i >= 0; i--) {
			WordAppearanceCount word = routineWords.get(i);
			Predictor predictor = predictors.get(word.word);
			if (predictor == null || predictor.skew < skewThreshold)
				routineWords.remove(i);
		}
	}

	public FeatureResponseGenerator.Field createWordMatchResponseField() {
		return new WordMatchResponseField();
	}

	public FeatureResponseGenerator.Field createNonEmptyRoutineResponseField() {
		return new NonEmptyRoutineResponseField();
	}
}
