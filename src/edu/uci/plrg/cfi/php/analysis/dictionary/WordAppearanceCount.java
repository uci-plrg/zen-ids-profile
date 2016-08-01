package edu.uci.plrg.cfi.php.analysis.dictionary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WordAppearanceCount {

	public static class SetBuilder {

		private final Map<String, WordAppearanceCount> set = new HashMap<String, WordAppearanceCount>();

		public void addWordAppearance(String word) {
			WordAppearanceCount count = set.get(word);
			if (count == null) {
				count = new WordAppearanceCount(word);
				set.put(word, count);
			} else {
				count.increment();
			}
		}

		public void addWordAppearances(WordAppearanceCount count) {
			WordAppearanceCount counted = set.get(count.word);
			if (counted == null) {
				counted = new WordAppearanceCount(count.word);
				set.put(count.word, counted);
			} else {
				counted.add(count);
			}
		}

		public void addWordAppearances(Iterable<WordAppearanceCount> words) {
			for (WordAppearanceCount word : words)
				addWordAppearances(word);
		}

		public List<WordAppearanceCount> serializeWords() {
			List<WordAppearanceCount> words = new ArrayList<WordAppearanceCount>(set.values());
			set.clear();
			return words;
		}
	}

	private int count = 1;
	public final int id;
	public final String word;

	WordAppearanceCount(String word) {
		this.word = word;

		long hash = word.hashCode();
		id = (int) ((hash & 0xffffffff) | (hash >> 0x20L));
	}

	void increment() {
		count++;
	}

	void add(WordAppearanceCount other) {
		count += other.count;
	}

	public int getCount() {
		return count;
	}
}
