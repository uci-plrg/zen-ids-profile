package edu.uci.plrg.cfi.php.analysis;

public class AnalysisException extends RuntimeException {

	public AnalysisException(String message, Object... args) {
		super(String.format(message, args));
	}

	public AnalysisException(Exception cause) {
		super(cause);
	}
}
