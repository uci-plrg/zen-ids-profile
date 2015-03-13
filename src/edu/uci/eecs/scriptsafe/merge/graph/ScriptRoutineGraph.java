package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;

public class ScriptRoutineGraph {

	public static boolean isDynamicRoutine(int routineHash) {
		return routineHash < 0;
	}

	public static int getDynamicRoutineIndex(int routineHash) {
		if (routineHash < 0)
			return (routineHash & 0x7fffffff);

		throw new MergeException("Attempt to extract a dynamic routine index from a routine hash that is not dynamic");
	}

	public static int constructDynamicHash(int index) {
		return (index | 0x80000000);
	}

	public final int hash;
	public final boolean isFragmentary;

	private final List<ScriptNode> nodes = new ArrayList<ScriptNode>();

	public ScriptRoutineGraph(int hash, boolean isFragmentary) {
		this.hash = hash;
		this.isFragmentary = isFragmentary;
	}

	public ScriptRoutineGraph copy(boolean isFragmentary) {
		return new ScriptRoutineGraph(hash, isFragmentary);
	}

	public ScriptRoutineGraph renameDynamicRoutine(int routineIndex, boolean isFragmentary) {
		ScriptRoutineGraph renamed = new ScriptRoutineGraph(ScriptRoutineGraph.constructDynamicHash(routineIndex),
				isFragmentary);
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
			throw new MergeException("Node counts differ at the same routine hash 0x%x!", hash);

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
