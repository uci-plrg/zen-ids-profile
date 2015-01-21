package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;

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

	public void addCallEdge(long fromRoutineId, ScriptNode fromNode, long toRoutineId) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null) {
			edges = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, edges);
		} else {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == RoutineEdge.Type.CALL && edge.getToRoutineId() == toRoutineId) {
					Log.message("Merging duplicate call edge from %s to routine 0x%x", fromNode, toRoutineId);
					return;
				}
			}
		}

		edgeCount++;
		edges.add(new RoutineEdge(fromRoutineId, fromNode.index, toRoutineId));
	}

	public void addExceptionEdge(long fromRoutineId, ScriptNode fromNode, long toRoutineId, int toRoutineIndex) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null) {
			edges = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, edges);
		} else {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == RoutineEdge.Type.THROW && edge.getToRoutineId() == toRoutineId
						&& ((RoutineExceptionEdge) edge).getToRoutineIndex() == toRoutineIndex) {
					Log.message("Merging duplicate throw edge from %s to %d in routine 0x%x", fromNode, toRoutineIndex,
							toRoutineId);
					return;
				}
			}
		}

		edgeCount++;
		edges.add(new RoutineExceptionEdge(fromRoutineId, fromNode.index, toRoutineId, toRoutineIndex));
	}
}
