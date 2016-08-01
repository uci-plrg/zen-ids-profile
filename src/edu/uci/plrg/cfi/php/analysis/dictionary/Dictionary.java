package edu.uci.plrg.cfi.php.analysis.dictionary;

public interface Dictionary {

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

	Evaluation evaluateRoutine(int hash, boolean hasHint, boolean hintAdmin);

	void addRoutine(int hash, boolean isAdmin);

	void reportSummary(int predictorCount);

	void reset();
}
