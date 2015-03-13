package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;

public class GraphEdgeSet {

	private final Map<ScriptNode, List<RoutineEdge>> outgoingEdges = new HashMap<ScriptNode, List<RoutineEdge>>();
	private int edgeCount = 0;

	public Iterable<RoutineEdge> getOutgoingEdges(ScriptNode fromNode) {
		Iterable<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null)
			return Collections.emptyList();
		else
			return edges;
	}

	public Iterable<List<RoutineEdge>> getOutgoingEdges() {
		return outgoingEdges.values();
	}

	public int getOutgoingEdgeCount() {
		return edgeCount;
	}

	public int getOutgoingEdgeCount(ScriptNode fromNode) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null)
			return 0;
		else
			return edges.size();
	}

	public boolean addCallEdge(int fromRoutineHash, ScriptNode fromNode, int toRoutineHash, int userLevel) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null) {
			edges = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, edges);
		} else {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == RoutineEdge.Type.CALL && edge.getToRoutineHash() == toRoutineHash) {
					if (edge.getUserLevel() == userLevel) {
						Log.message("Skipping duplicate call edge from %s to routine 0x%x at user level %d", fromNode,
								toRoutineHash, userLevel);
						if (ScriptMergeWatchList.watchAny(fromRoutineHash, fromNode.index)
								|| ScriptMergeWatchList.watch(toRoutineHash)) {
							Log.log("Skipping duplicate call edge %s -> %s", edge.printFromNode(), edge.printToNode());
						}
					} else if (edge.getUserLevel() > userLevel) {
						edge.setUserLevel(userLevel);
					}
					return false;
				}
			}
		}

		edgeCount++;
		RoutineEdge newEdge = new RoutineEdge(fromRoutineHash, fromNode.index, toRoutineHash, userLevel);
		edges.add(newEdge);
		if (ScriptMergeWatchList.watchAny(fromRoutineHash, fromNode.index) || ScriptMergeWatchList.watch(toRoutineHash)) {
			Log.log("Add call edge to set: %s (op 0x%x) -> %s", newEdge.printFromNode(), fromNode.opcode,
					newEdge.printToNode());
		}
		return true;
	}

	public boolean addExceptionEdge(int fromRoutineHash, ScriptNode fromNode, int toRoutineHash, int toRoutineIndex,
			int userLevel) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null) {
			edges = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, edges);
		} else {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == RoutineEdge.Type.THROW && edge.getToRoutineHash() == toRoutineHash
						&& ((RoutineExceptionEdge) edge).getToRoutineIndex() == toRoutineIndex) {
					if (edge.getUserLevel() == userLevel) {
						Log.message("Merging duplicate throw edge from %s to %d in routine 0x%x at user level %d",
								fromNode, toRoutineIndex, toRoutineHash, userLevel);
					} else if (edge.getUserLevel() > userLevel) {
						edge.setUserLevel(userLevel);
					}
					return false;
				}
			}
		}

		edgeCount++;
		RoutineExceptionEdge newEdge = new RoutineExceptionEdge(fromRoutineHash, fromNode.index, toRoutineHash,
				toRoutineIndex, userLevel);
		edges.add(newEdge);
		if (ScriptMergeWatchList.watchAny(fromRoutineHash, fromNode.index) || ScriptMergeWatchList.watch(toRoutineHash)) {
			Log.log("Add exception edge to set: %s -> %s", newEdge.printFromNode(), newEdge.printToNode());
		}
		return true;
	}
}
