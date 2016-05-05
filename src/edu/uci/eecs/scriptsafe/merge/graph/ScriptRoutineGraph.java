package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;

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
	public final RoutineId id;
	public final boolean isNewUserLevelSample;

	private final List<ScriptNode> nodes = new ArrayList<ScriptNode>();

	public ScriptRoutineGraph(int hash, RoutineId id, boolean isNewUserLevelSample) {
		this.hash = hash;
		this.id = id;
		this.isNewUserLevelSample = isNewUserLevelSample;
	}

	public ScriptRoutineGraph copy(boolean isFragmentary) {
		return new ScriptRoutineGraph(hash, id, isFragmentary);
	}

	public ScriptRoutineGraph renameDynamicRoutine(int routineIndex, RoutineId id, boolean isFragmentary) {
		ScriptRoutineGraph renamed = new ScriptRoutineGraph(ScriptRoutineGraph.constructDynamicHash(routineIndex), id,
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
		
		if (hash == 0x214a43f0)
			Log.log("hm?");
		
		if (nodes.size() != other.nodes.size())
			throw new MergeException("Node counts differ at the same routine hash 0x%x: %d vs. %d!", hash,
					nodes.size(), other.nodes.size());

		if (other.isNewUserLevelSample) {
			for (int i = 0; i < nodes.size(); i++) {
				ScriptNode thisNode = nodes.get(i);
				ScriptNode otherNode = other.nodes.get(i);
				thisNode.verifyCompatible(otherNode);
				if (otherNode.getNodeUserLevel() < thisNode.getNodeUserLevel())
					thisNode.setNodeUserLevel(otherNode.getNodeUserLevel());

			}
		} else {
			for (int i = 0; i < nodes.size(); i++)
				nodes.get(i).verifyEqual(other.nodes.get(i));
		}
	}
}
