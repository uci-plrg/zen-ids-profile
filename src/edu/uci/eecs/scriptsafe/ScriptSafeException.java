package edu.uci.eecs.scriptsafe;

public class ScriptSafeException extends RuntimeException {

	public ScriptSafeException(String format, Object... args) {
		super(String.format(format, args));
	}
}
