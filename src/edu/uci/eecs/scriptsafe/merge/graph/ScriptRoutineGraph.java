package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.scriptsafe.merge.MergeException;

public class ScriptRoutineGraph {

	public static final int DYNAMIC_UNIT_HASH = 0;

	public static boolean isDynamicRoutine(long routineId) {
		return (int) (routineId >> 0x20) == 0;
	}

	public static int getDynamicRoutineId(long routineId) {
		if (!isDynamicRoutine(routineId))
			return -1;

		return (int) (routineId & 0xffffffffL);
	}

	public static long constructId(int unitHash, int routineHash) {
		return ((long) unitHash << 0x20) | routineHash;
	}

	public static long constructDynamicId(int id) {
		return constructId(DYNAMIC_UNIT_HASH, id);
	}

	public final int unitHash;
	public final int routineHash;
	public final Long id;

	private boolean redundant = false;
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

	public Iterable<ScriptNode> getNodes() {
		return nodes;
	}

	public void clearNodes() {
		nodes.clear();
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

	public void verifySameRoutine(ScriptRoutineGraph other) {
		if (nodes.size() != other.nodes.size())
			throw new MergeException("Node counts differ at the same routine id 0x%x!", id);

		for (int i = 0; i < nodes.size(); i++)
			nodes.get(i).verifyEqual(other.nodes.get(i));
	}
}
