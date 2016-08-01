package edu.uci.plrg.cfi.php.merge.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.php.merge.MergeException;
import edu.uci.plrg.cfi.php.merge.ScriptMergeWatchList;

public class GraphEdgeSet {

	public enum AddEdgeResultType {
		NEW_EDGE,
		LOWER_USER_LEVEL
	}

	public static abstract class AddEdgeResult {
		public final AddEdgeResultType type;
		public final RoutineEdge edge;

		public AddEdgeResult(AddEdgeResultType type, RoutineEdge edge) {
			this.type = type;
			this.edge = edge;
		}
	}

	public static class NewEdgeResult extends AddEdgeResult {
		public NewEdgeResult(RoutineEdge newEdge) {
			super(AddEdgeResultType.NEW_EDGE, newEdge);
		}
	}

	public static class LowerUserLevelResult extends AddEdgeResult {
		public final int fromUserLevel, toUserLevel;

		public LowerUserLevelResult(RoutineEdge edge, int toUserLevel) {
			super(AddEdgeResultType.LOWER_USER_LEVEL, edge);

			this.fromUserLevel = edge.getUserLevel();
			this.toUserLevel = toUserLevel;
		}
	}

	private final Map<ScriptNode, List<RoutineEdge>> outgoingEdges = new HashMap<ScriptNode, List<RoutineEdge>>();
	private final Map<Integer, List<RoutineEdge>> incomingEdges = new HashMap<Integer, List<RoutineEdge>>();
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

	public int getIncomingEdgeCount(int routineHash) {
		List<RoutineEdge> edges = incomingEdges.get(routineHash);
		return (edges == null) ? 0 : edges.size();
	}

	public Iterable<RoutineEdge> getIncomingEdges(int routineHash) {
		List<RoutineEdge> edges = incomingEdges.get(routineHash);
		if (edges == null)
			return Collections.emptyList();
		else
			return edges;
	}

	public int getMinUserLevel(int routineHash) {
		int min = Integer.MAX_VALUE;
		List<RoutineEdge> edges = incomingEdges.get(routineHash);
		if (edges != null) {
			for (RoutineEdge edge : edges) {
				if (edge.getUserLevel() < min)
					min = edge.getUserLevel();
			}
		}
		return min;
	}

	public AddEdgeResult addCallEdge(int fromRoutineHash, ScriptNode fromNode, int toRoutineHash, int userLevel) {
		return addCallEdge(fromRoutineHash, fromNode, toRoutineHash, userLevel, false);
	}

	public AddEdgeResult addCallEdge(int fromRoutineHash, ScriptNode fromNode, int toRoutineHash, int userLevel,
			boolean evaluating) {
		if (fromNode == null)
			throw new MergeException("Cannot create a routine edge from a missing from node.");
		
		List<RoutineEdge> nodeOutgoing = outgoingEdges.get(fromNode);
		if (nodeOutgoing == null) {
			nodeOutgoing = new ArrayList<RoutineEdge>();
			outgoingEdges.put(fromNode, nodeOutgoing);
		} else {
			for (RoutineEdge edge : nodeOutgoing) {
				if (edge.getEntryType() == RoutineEdge.Type.CALL && edge.getToRoutineHash() == toRoutineHash) {
					if (edge.getUserLevel() == userLevel) {
						Log.message("Skipping duplicate call edge from %s to routine 0x%x at user level %d", fromNode,
								toRoutineHash, userLevel);
						if (ScriptMergeWatchList.watchAny(fromRoutineHash, fromNode.index)
								|| ScriptMergeWatchList.watch(toRoutineHash)) {
							Log.log("Skipping duplicate call edge %s -%s-> %s", edge.printFromNode(),
									edge.printUserLevel(), edge.printToNode());
						}
					} else if (userLevel < edge.getUserLevel()) {
						LowerUserLevelResult result = new LowerUserLevelResult(edge, userLevel);
						if (!evaluating)
							edge.setUserLevel(userLevel);
						return result;
					}
					return null;
				}
			}
		}

		edgeCount++;
		RoutineEdge newEdge = new RoutineEdge(fromRoutineHash, fromNode.index, toRoutineHash, userLevel);

		if (!evaluating) {
			nodeOutgoing.add(newEdge);

			List<RoutineEdge> nodeIncoming = incomingEdges.get(toRoutineHash);
			if (nodeIncoming == null) {
				nodeIncoming = new ArrayList<RoutineEdge>();
				incomingEdges.put(toRoutineHash, nodeIncoming);
			}
			nodeIncoming.add(newEdge);

			if (ScriptMergeWatchList.watchAny(fromRoutineHash, fromNode.index)
					|| ScriptMergeWatchList.watch(toRoutineHash)) {
				Log.log("Add call edge to set: %s (op 0x%x) -%s-> %s", newEdge.printFromNode(), fromNode.opcode,
						newEdge.printUserLevel(), newEdge.printToNode());
			}
		}

		return new NewEdgeResult(newEdge);
	}

	public AddEdgeResult addExceptionEdge(int fromRoutineHash, ScriptNode fromNode, int toRoutineHash,
			int toRoutineIndex, int userLevel) {
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
					} else if (userLevel < edge.getUserLevel()) {
						LowerUserLevelResult result = new LowerUserLevelResult(edge, userLevel);
						edge.setUserLevel(userLevel);
						return result;
					}
					return null;
				}
			}
		}

		edgeCount++;
		RoutineExceptionEdge newEdge = new RoutineExceptionEdge(fromRoutineHash, fromNode.index, toRoutineHash,
				toRoutineIndex, userLevel);
		edges.add(newEdge);

		List<RoutineEdge> nodeIncoming = incomingEdges.get(toRoutineHash);
		if (nodeIncoming == null) {
			nodeIncoming = new ArrayList<RoutineEdge>();
			incomingEdges.put(toRoutineHash, nodeIncoming);
		}
		nodeIncoming.add(newEdge);

		if (ScriptMergeWatchList.watchAny(fromRoutineHash, fromNode.index) || ScriptMergeWatchList.watch(toRoutineHash)) {
			Log.log("Add exception edge to set: %s -> %s", newEdge.printFromNode(), newEdge.printToNode());
		}
		return new NewEdgeResult(newEdge);
	}
}
