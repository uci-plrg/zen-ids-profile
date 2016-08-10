package edu.uci.plrg.cfi.php.merge;

public class MergeException extends RuntimeException {

	public MergeException(String message, Object... args) {
		super(String.format(message, args));
	}
	
	public MergeException(Exception cause) {
		super(cause);
	}	
}
