package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

public class ScriptRoutineGraph {

	public static final int EVAL_UNIT_HASH = 0;

	public static boolean isEval(long routineId) {
		return (routineId >> 16) == 0;
	}
	
	public static int getEvalId(long routineId) {
		if (!isEval(routineId))
			return -1;
		
		return (int) (routineId & 0xffffffff);
	}

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

	public ScriptRoutineGraph copy() {
		return new ScriptRoutineGraph(unitHash, routineHash);
	}
	
	public ScriptRoutineGraph rename(int unitHash, int routineHash) {
		ScriptRoutineGraph renamed = new ScriptRoutineGraph(unitHash, routineHash);
		renamed.nodes.addAll(nodes);
		return renamed;
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
	
	public boolean isSameRoutine(ScriptRoutineGraph other) {
		if (nodes.size() != other.nodes.size())
			return false;
		
		for (int i = 0; i < nodes.size(); i++) {
			if (!nodes.get(i).isEqual(other.nodes.get(i)))
				return false;
		}
		return true;
	}
}
