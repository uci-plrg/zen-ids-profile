package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

public class ScriptRoutineGraph {

	public static long constructId(int unitHash, int routineHash) {
		return ((long) unitHash << 16) | routineHash;
	}

	public final int unitHash;
	public final int routineHash;
	public final Long id;

	private final List<ScriptNode> nodes = new ArrayList<ScriptNode>();

	public ScriptRoutineGraph(int unitHash, int routineHash) {
		this.unitHash = unitHash;
		this.routineHash = routineHash;

		this.id = constructId(unitHash, routineHash);
	}
	
	public ScriptRoutineGraph copy()
	{
		return new ScriptRoutineGraph(unitHash, routineHash);
	}

	public void addNode(ScriptNode node) {
		nodes.add(node);
	}

	public ScriptNode getNode(int index) {
		return nodes.get(index);
	}
	
	public int getNodeCount() {
		return nodes.size();
	}
}
