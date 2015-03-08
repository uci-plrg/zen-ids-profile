package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;

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
		if (routineHash < 0)
			return (((long) unitHash << 0x20) | 0xffffffffL) & routineHash; // dislike!
		else
			return ((long) unitHash << 0x20) | routineHash;
	}

	public static long constructDynamicId(int id) {
		return constructId(DYNAMIC_UNIT_HASH, id);
	}

	public static int extractUnitHash(long routineId) {
		return (int) (routineId >> 0x10);
	}

	public static int extractRoutineHash(long routineId) {
		return (int) (routineId & 0xffffffffL);
	}

	public final int unitHash;
	public final int routineHash;
	public final Long id;
	public final boolean isFragmentary;

	private boolean redundant = false;
	private final List<ScriptNode> nodes = new ArrayList<ScriptNode>();

	public ScriptRoutineGraph(int unitHash, int routineHash, boolean isFragmentary) {
		this.unitHash = unitHash;
		this.routineHash = routineHash;
		this.isFragmentary = isFragmentary;

		this.id = constructId(unitHash, routineHash);

		if (((int) (id >> 0x20)) == 0xffffffff)
			Log.log("stop!");
	}

	public ScriptRoutineGraph copy(boolean isFragmentary) {
		return new ScriptRoutineGraph(unitHash, routineHash, isFragmentary);
	}

	public ScriptRoutineGraph rename(int unitHash, int routineHash, boolean isFragmentary) {
		ScriptRoutineGraph renamed = new ScriptRoutineGraph(unitHash, routineHash, isFragmentary);
		renamed.nodes.addAll(nodes);
		return renamed;
	}

	public void addNode(ScriptNode node) {
		if (node.index < nodes.size()) {
			nodes.set(node.index, node);
		} else if (node.index == nodes.size()) {
			nodes.add(node);
		} else {
			throw new MergeException("Unexpected node at index %d in a routine of (current) size %d!", node.index,
					nodes.size());
		}
	}

	public ScriptNode getNode(int index) {
		// if (index >= nodes.size() || index < 0)
		// Log.spot("halt!");
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

	public void mergeRoutine(ScriptRoutineGraph other) {
		if (nodes.size() != other.nodes.size())
			throw new MergeException("Node counts differ at the same routine id 0x%x!", id);

		if (other.isFragmentary) {
			for (int i = 0; i < nodes.size(); i++) {
				ScriptNode thisNode = nodes.get(i);
				ScriptNode otherNode = other.nodes.get(i);
				thisNode.verifyCompatible(otherNode);
				if (thisNode.type == Type.BRANCH) {
					ScriptBranchNode thisBranchNode = (ScriptBranchNode) thisNode;
					ScriptBranchNode otherBranchNode = (ScriptBranchNode) otherNode;
					if (otherBranchNode.getBranchUserLevel() > thisBranchNode.getBranchUserLevel())
						thisBranchNode.setBranchUserLevel(otherBranchNode.getBranchUserLevel());
				}

			}
		} else {
			for (int i = 0; i < nodes.size(); i++)
				nodes.get(i).verifyEqual(other.nodes.get(i));
		}
	}
}
