package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;

public class GraphEdgeSet {

	private final Map<ScriptNode, List<RoutineEdge>> outgoingEdges = new HashMap<ScriptNode, List<RoutineEdge>>();

	public Iterable<RoutineEdge> getOutgoingEdges(ScriptNode fromNode) {
		return outgoingEdges.get(fromNode);
	}

	public Iterable<List<RoutineEdge>> getOutgoingEdges() {
		return outgoingEdges.values();
	}

	public void addCallEdge(long fromRoutineId, ScriptNode fromNode, long toRoutineId) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null) {
			edges = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, edges);
		} else {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == RoutineEdge.Type.CALL && edge.getToRoutineId() == toRoutineId) {
					Log.log("Merging duplicate call edge from %s to routine 0x%x", fromNode, toRoutineId);
					return;
				}
			}
		}
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
					Log.log("Merging duplicate throw edge from %s to %d in routine 0x%x", fromNode, toRoutineIndex,
							toRoutineId);
					return;
				}
			}
		}
		edges.add(new RoutineExceptionEdge(fromRoutineId, fromNode.index, toRoutineId, toRoutineIndex));
	}
}
