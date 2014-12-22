package edu.uci.eecs.scriptsafe.merge.graph;

public class ScriptNode {

	public final int opcode;
	
	private ScriptNode next;

	public ScriptNode(int opcode) {
		this.opcode = opcode;
	}

	public ScriptNode getNext() {
		return next;
	}

	public void setNext(ScriptNode next) {
		this.next = next;
	}
}
