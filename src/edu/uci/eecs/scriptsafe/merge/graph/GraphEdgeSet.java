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

	public void addCallEdge(long fromRoutineId, ScriptNode fromNode, long toRoutineId, int userLevel) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null) {
			edges = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, edges);
		} else {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == RoutineEdge.Type.CALL && edge.getToRoutineId() == toRoutineId) {
					if (edge.getUserLevel() == userLevel) {
						Log.message("Skipping duplicate call edge from %s to routine 0x%x at user level %d", fromNode,
								toRoutineId, userLevel);
						if (ScriptMergeWatchList.getInstance().watch(fromRoutineId, fromNode.index)) {
							Log.log("Skipping duplicate call edge %s -> %s", edge.printFromNode(), edge.printToNode());
						}
					} else if (edge.getUserLevel() > userLevel) {
						edge.setUserLevel(userLevel);
					}
					return;
				}
			}
		}

		edgeCount++;
		RoutineEdge newEdge = new RoutineEdge(fromRoutineId, fromNode.index, toRoutineId, userLevel);
		edges.add(newEdge);
		if (ScriptMergeWatchList.getInstance().watch(fromRoutineId, fromNode.index)) {
			Log.log("Add call edge to set: %s -> %s", newEdge.printFromNode(), newEdge.printToNode());
		}
	}

	public void addExceptionEdge(long fromRoutineId, ScriptNode fromNode, long toRoutineId, int toRoutineIndex,
			int userLevel) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null) {
			edges = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, edges);
		} else {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == RoutineEdge.Type.THROW && edge.getToRoutineId() == toRoutineId
						&& ((RoutineExceptionEdge) edge).getToRoutineIndex() == toRoutineIndex) {
					if (edge.getUserLevel() == userLevel) {
						Log.message("Merging duplicate throw edge from %s to %d in routine 0x%x at user level %d",
								fromNode, toRoutineIndex, toRoutineId, userLevel);
					} else if (edge.getUserLevel() > userLevel) {
						edge.setUserLevel(userLevel);
					}
					return;
				}
			}
		}

		edgeCount++;
		RoutineExceptionEdge newEdge = new RoutineExceptionEdge(fromRoutineId, fromNode.index, toRoutineId,
				toRoutineIndex, userLevel);
		edges.add(newEdge);
		if (ScriptMergeWatchList.getInstance().watch(fromRoutineId, fromNode.index)) {
			Log.log("Add exception edge to set: %s -> %s", newEdge.printFromNode(), newEdge.printToNode());
		}
	}
}
