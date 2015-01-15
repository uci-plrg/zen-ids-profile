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

	public void addCallEdge(ScriptNode fromNode, ScriptRoutineGraph target) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null) {
			edges = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, edges);
		} else {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == RoutineEdge.Type.CALL && edge.getRoutineId() == target.id) {
					Log.log("Merging duplicate call edge from %s to %s", fromNode, target);
					return;
				}
			}
		}
		edges.add(new RoutineEdge(target.id));
	}

	public void addExceptionEdge(ScriptNode fromNode, ScriptRoutineGraph targetRoutine, int targetIndex) {
		List<RoutineEdge> edges = outgoingEdges.get(fromNode);
		if (edges == null) {
			edges = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, edges);
		} else {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == RoutineEdge.Type.THROW && edge.getRoutineId() == targetRoutine.id
						&& ((RoutineExceptionEdge) edge).getToRoutineIndex() == targetIndex) {
					Log.log("Merging duplicate throw edge from %s to %d in %s", fromNode, targetIndex,
							targetRoutine);
					return;
				}
			}
		}
		edges.add(new RoutineEdge(targetRoutine.id));
	}
}
