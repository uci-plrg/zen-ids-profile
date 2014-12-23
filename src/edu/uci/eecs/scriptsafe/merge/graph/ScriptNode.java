package edu.uci.eecs.scriptsafe.merge.graph;

public class ScriptNode {

	public final int opcode;
	public final int index;
	
	private ScriptNode next;

	public ScriptNode(int opcode, int index) {
		this.opcode = opcode;
		this.index = index;
	}

	public ScriptNode getNext() {
		return next;
	}

	public void setNext(ScriptNode next) {
		this.next = next;
	}
}
