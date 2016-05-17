package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.DatasetMerge;
import edu.uci.eecs.scriptsafe.merge.MergeException;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineId;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Opcode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.OpcodeTargetType;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

class ScriptRunLoader {

	private static class RawOpcodeEdge {
		final int routineHash;
		final int fromIndex;
		final int toIndex;
		final int userLevel;

		public RawOpcodeEdge(int routineHash, int fromIndex, int toIndex, int userLevel) {
			this.routineHash = routineHash;
			this.fromIndex = fromIndex;
			this.toIndex = toIndex;
			this.userLevel = userLevel;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + fromIndex;
			result = prime * result + routineHash;
			result = prime * result + toIndex;
			result = prime * result + userLevel;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RawOpcodeEdge other = (RawOpcodeEdge) obj;
			if (fromIndex != other.fromIndex)
				return false;
			if (routineHash != other.routineHash)
				return false;
			if (toIndex != other.toIndex)
				return false;
			if (userLevel != other.userLevel)
				return false;
			return true;
		}
	}

	private static class RawRoutineEdge {
		final int fromRoutineHash;
		final int fromIndex;
		final int toRoutineHash;
		final int toIndex;
		final int userLevel;

		public RawRoutineEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, int toIndex, int userLevel) {
			this.fromRoutineHash = fromRoutineHash;
			this.fromIndex = fromIndex;
			this.toRoutineHash = toRoutineHash;
			this.toIndex = toIndex;
			this.userLevel = userLevel;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + fromIndex;
			result = prime * result + fromRoutineHash;
			result = prime * result + toIndex;
			result = prime * result + toRoutineHash;
			result = prime * result + userLevel;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			RawRoutineEdge other = (RawRoutineEdge) obj;
			if (fromIndex != other.fromIndex)
				return false;
			if (fromRoutineHash != other.fromRoutineHash)
				return false;
			if (toIndex != other.toIndex)
				return false;
			if (toRoutineHash != other.toRoutineHash)
				return false;
			if (userLevel != other.userLevel)
				return false;
			return true;
		}
	}

	private static class RawRoutineGraph {
		final int hash;
		final Map<Integer, Set<RawOpcodeEdge>> opcodeEdges = new HashMap<Integer, Set<RawOpcodeEdge>>();
		final Map<Integer, Set<RawRoutineEdge>> routineEdges = new HashMap<Integer, Set<RawRoutineEdge>>();

		public RawRoutineGraph(int hash) {
			this.hash = hash;
		}

		void addRawEdge(RawOpcodeEdge edge) {
			Set<RawOpcodeEdge> existingEdges = opcodeEdges.get(edge.fromIndex);
			if (existingEdges == null) {
				existingEdges = new HashSet<RawOpcodeEdge>();
				opcodeEdges.put(edge.fromIndex, existingEdges);
			}
			existingEdges.add(edge);
		}

		void addRawEdge(RawRoutineEdge edge) {
			Set<RawRoutineEdge> existingEdges = routineEdges.get(edge.fromIndex);
			if (existingEdges == null) {
				existingEdges = new HashSet<RawRoutineEdge>();
				routineEdges.put(edge.fromIndex, existingEdges);
			}
			existingEdges.add(edge);
		}
	}

	private class ScriptNodeLoadContext implements ScriptNodeLoader.LoadContext {
		private ScriptFlowGraph flowGraph;

		void setFlowGraph(ScriptFlowGraph flowGraph) {
			this.flowGraph = flowGraph;
		}

		@Override
		public ScriptRoutineGraph createRoutine(int routineHash) {
			ScriptRoutineGraph routine = new ScriptRoutineGraph(routineHash,
					RoutineId.Cache.INSTANCE.getId(routineHash), flowGraph.isNewUserLevelSample);
			flowGraph.addRoutine(routine);
			return routine;
		}

		@Override
		public ScriptRoutineGraph getRoutine(int routineHash) {
			ScriptRoutineGraph routine = flowGraph.getRoutine(routineHash);
			if (routine != null && preloadedRoutines.contains(routine.hash)) { // were nodes copied from the right?
				routine.clearNodes(); // have a copy on the left, so remove copied nodes
				preloadedRoutines.remove(routine.hash); // don't clear it next time
			}
			return routine;
		}
	}

	private final Set<Integer> preloadedRoutines = new HashSet<Integer>();
	private final Map<Integer, RawRoutineGraph> rawGraphs = new HashMap<Integer, RawRoutineGraph>();
	private final Map<Integer, Integer> pendingNodeUserLevels = new HashMap<Integer, Integer>();
	private DatasetMerge.Side side;

	private final ScriptNodeLoadContext nodeLoadContext = new ScriptNodeLoadContext();
	private final ScriptNodeLoader nodeLoader = new ScriptNodeLoader(nodeLoadContext);

	ScriptRunLoader() {
	}

	private RawRoutineGraph getRawGraph(Integer hash) {
		RawRoutineGraph graph = rawGraphs.get(hash);
		if (graph == null) {
			graph = new RawRoutineGraph(hash);
			rawGraphs.put(hash, graph);
		}
		return graph;
	}

	private void pendNodeUserLevel(int nodeIndex, int userLevel) {
		Integer alreadyPendingUserLevel = pendingNodeUserLevels.get(nodeIndex);
		if (alreadyPendingUserLevel == null || alreadyPendingUserLevel > userLevel)
			pendingNodeUserLevels.put(nodeIndex, userLevel);
	}

	void loadRun(ScriptRunFiles run, ScriptFlowGraph graph, DatasetMerge.Side side, boolean shallow) throws IOException {
		preloadedRoutines.clear();
		for (ScriptRoutineGraph preloadedRoutine : graph.getRoutines())
			preloadedRoutines.add(preloadedRoutine.hash);
		rawGraphs.clear();
		this.side = side;

		loadOpcodeEdges(run, !shallow);
		if (!shallow)
			loadRoutineEdges(run, graph);

		RoutineId.Cache.INSTANCE.load(run.routineCatalog);
		nodeLoadContext.setFlowGraph(graph);
		nodeLoader.loadNodes(run.nodeFile);

		if (!shallow)
			linkNodes(graph);
	}

	private void loadOpcodeEdges(ScriptRunFiles run, boolean loadUserLevel) throws IOException {
		int routineHash, fromIndex, toIndex, userLevel;
		RawRoutineGraph graph;
		LittleEndianInputStream input = new LittleEndianInputStream(run.opcodeEdgeFile);

		while (input.ready(0xc)) {
			routineHash = input.readInt();

			fromIndex = input.readInt();
			userLevel = loadUserLevel ? (fromIndex >>> 26) : 0;
			fromIndex = (fromIndex & 0x3ffffff);
			toIndex = input.readInt();

			graph = getRawGraph(routineHash);
			graph.addRawEdge(new RawOpcodeEdge(routineHash, fromIndex, toIndex, userLevel));
		}

		if (input.ready()) {
			Log.error("Input file " + run.opcodeEdgeFile.getAbsolutePath() + " has trailing data!");
		}
		input.close();
	}

	private boolean isFallThrough(ScriptNode fromNode, RawOpcodeEdge edge) {
		ScriptNode.Opcode opcode = ScriptNode.Opcode.forCode(fromNode.opcode);
		if (opcode == null)
			return false;

		switch (opcode) {
			case ZEND_ASSIGN_DIM:
			case ZEND_NEW:
			case ZEND_JMP_SET:
				return edge.toIndex <= (edge.fromIndex + 0x10);
			default:
				return false;
		}
	}

	private void linkNodes(ScriptFlowGraph graph) {
		ScriptRoutineGraph routine, toRoutine;
		ScriptNode fromNode;
		ScriptBranchNode branchNode;
		for (RawRoutineGraph rawGraph : rawGraphs.values()) {

			routine = graph.getRoutine(rawGraph.hash);
			pendingNodeUserLevels.clear();
			
			if (routine == null)
				break;

			for (Set<RawOpcodeEdge> edges : rawGraph.opcodeEdges.values()) {
				for (RawOpcodeEdge edge : edges) {
					if (routine == null) {
						Log.warn("Cannot find routine for hash 0x%x. Skipping it.", edge.routineHash);
						continue;
					} else if (edge.fromIndex > routine.getNodeCount()) {
						Log.warn(
								"Edge originates at a non-existent node with index %d in a routine of size %d. Skipping it.",
								edge.fromIndex, routine.getNodeCount());
						continue;
					} else {
						fromNode = routine.getNode(edge.fromIndex);
					}

					if (fromNode.opcode == 0x3e) {
						Log.error("Found a branch from a return node!");
						continue;
					}

					if (!(fromNode instanceof ScriptBranchNode)) {
						if (!isFallThrough(fromNode, edge)) {
							Log.warn(
									"Exception caught within throwing routine: %d -> %d from opcode 0x%x in routine 0x%x!",
									edge.fromIndex, edge.toIndex, fromNode.opcode, edge.routineHash);
							graph.edges.addExceptionEdge(edge.routineHash, fromNode, edge.routineHash, edge.toIndex,
									edge.userLevel);
						}
						continue;
					}

					branchNode = (ScriptBranchNode) routine.getNode(edge.fromIndex);
					if (edge.toIndex > routine.getNodeCount()) {
						Log.warn(
								"Edge points to a non-existent node with index %d in a routine of size %d. Skipping it.",
								edge.toIndex, routine.getNodeCount());
					} else {
						if (branchNode.isFallThrough(edge.toIndex)) {
							pendNodeUserLevel(edge.toIndex, edge.userLevel);
						} else {
							branchNode.setTarget(routine.getNode(edge.toIndex));
							if (edge.toIndex > branchNode.index || branchNode.opcode == ScriptNode.Opcode.ZEND_JMP.code)
								pendNodeUserLevel(edge.toIndex, edge.userLevel);
						}
					}
					branchNode.setBranchUserLevel(edge.userLevel);

					Log.message("User level %d on %d->%d in routine 0x%x", branchNode.getBranchUserLevel(),
							edge.fromIndex, edge.toIndex, routine.hash);

					if (edge.toIndex > routine.getNodeCount()) {
						Log.warn(
								"Edge points to a non-existent node with index %d in a routine of size %d. Skipping it.",
								edge.toIndex, routine.getNodeCount());
					} else {
						if (routine.getNode(edge.toIndex).index != edge.toIndex) {
							throw new MergeException("Incorrect node index: expected %d but found %d", edge.toIndex,
									routine.getNode(edge.toIndex).index);
						}
					}
				}
			}

			Integer pendingUserLevel;
			int propagatingUserLevel = ScriptNode.USER_LEVEL_TOP;
			Opcode nodeOpcode;
			for (ScriptNode node : routine.getNodes()) {
				nodeOpcode = ScriptNode.Opcode.forCode(node.opcode);
				pendingUserLevel = pendingNodeUserLevels.get(node.index);
				if (pendingUserLevel != null && pendingUserLevel < propagatingUserLevel)
					propagatingUserLevel = pendingUserLevel;
				if (propagatingUserLevel < node.getNodeUserLevel())
					node.setNodeUserLevel(propagatingUserLevel);
				if (node.type == Type.BRANCH || nodeOpcode.isReturn()) {
					propagatingUserLevel = ScriptNode.USER_LEVEL_TOP;

					if (node.type == Type.BRANCH) {
						branchNode = (ScriptBranchNode) node;
						if (branchNode.opcode == Opcode.ZEND_JMP.code && branchNode.getTargetIndex() > branchNode.index)
							pendNodeUserLevel(branchNode.getTargetIndex(), branchNode.getNodeUserLevel());

						// hack for silly nop JNZ
						if (nodeOpcode.targetType == OpcodeTargetType.REQUIRED && branchNode.getTarget() == null)
							branchNode.setTarget(branchNode.getNext());
					}
				}
			}

			for (Set<RawRoutineEdge> edges : rawGraph.routineEdges.values()) {
				for (RawRoutineEdge edge : edges) {
					if (edge.fromIndex >= routine.getNodeCount()) {
						Log.error(
								"Found an edge from index %d in 0x%x|0x%x, which only has %d nodes! Skipping it for now.",
								edge.fromIndex, edge.fromRoutineHash, edge.fromRoutineHash, routine.getNodeCount());
						continue;
					}
					fromNode = routine.getNode(edge.fromIndex);
					toRoutine = graph.getRoutine(edge.toRoutineHash);
					if (toRoutine == null) {
						Log.log("Skipping edge from 0x%x @%d to unknown routine 0x%x", routine.hash, edge.fromIndex,
								edge.toRoutineHash);
						continue;
						// throw new IllegalArgumentException(String.format(
						// "Found a routine edge to an unknown routine 0x%x", edge.toRoutineHash));
					}

					if (ScriptMergeWatchList.watchAny(edge.fromRoutineHash, edge.fromIndex)
							|| ScriptMergeWatchList.watch(edge.toRoutineHash)) {
						Log.log("Loader added routine edge to the %s graph from op 0x%x: 0x%x|0x%x %d -%s-> 0x%x|0x%x",
								side, routine.getNode(edge.fromIndex).opcode, edge.fromRoutineHash,
								edge.fromRoutineHash, edge.fromIndex, RoutineEdge.printUserLevel(edge.userLevel),
								edge.toRoutineHash, edge.toRoutineHash);
					}

					if (edge.toIndex == 0)
						graph.edges.addCallEdge(routine.hash, fromNode, toRoutine.hash, edge.userLevel);
					else
						graph.edges.addExceptionEdge(routine.hash, fromNode, toRoutine.hash, edge.toIndex,
								edge.userLevel);
				}
			}
		}
	}

	private void loadRoutineEdges(ScriptRunFiles run, ScriptFlowGraph graph) throws IOException {
		int fromRoutineHash, fromIndex, toRoutineHash, toIndex, userLevel;
		RawRoutineGraph routine;
		LittleEndianInputStream input = new LittleEndianInputStream(run.routineEdgeFile);

		while (input.ready(0x10)) {
			fromRoutineHash = input.readInt();
			fromIndex = input.readInt();
			userLevel = (fromIndex >>> 26);
			fromIndex = (fromIndex & 0x3ffffff);
			toRoutineHash = input.readInt();
			toIndex = input.readInt();

			routine = getRawGraph(fromRoutineHash);
			routine.addRawEdge(new RawRoutineEdge(fromRoutineHash, fromIndex, toRoutineHash, toIndex, userLevel));

			if (ScriptMergeWatchList.watchAny(fromRoutineHash, fromIndex) || ScriptMergeWatchList.watch(toRoutineHash)) {
				Log.log("Loaded routine edge 0x%x @%d -%s-> 0x%x", fromRoutineHash, fromIndex,
						RoutineEdge.printUserLevel(userLevel), toRoutineHash);
			}
		}

		if (input.ready()) {
			Log.error("Input file " + run.routineEdgeFile.getAbsolutePath() + " has trailing data!");
		}
		input.close();
	}
}
