package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

class ScriptRunLoader {

	private static class RawOpcodeEdge {
		final long routineId;
		final int fromIndex;
		final int toIndex;
		final int userLevel;

		public RawOpcodeEdge(long routineId, int fromIndex, int toIndex, int userLevel) {
			this.routineId = routineId;
			this.fromIndex = fromIndex;
			this.toIndex = toIndex;
			this.userLevel = userLevel;
		}
	}

	private static class RawRoutineEdge {
		final long fromRoutineId;
		final int fromIndex;
		final long toRoutineId;
		final int toIndex;
		final int userLevel;

		public RawRoutineEdge(long fromRoutineId, int fromIndex, long toRoutineId, int toIndex, int userLevel) {
			this.fromRoutineId = fromRoutineId;
			this.fromIndex = fromIndex;
			this.toRoutineId = toRoutineId;
			this.toIndex = toIndex;
			this.userLevel = userLevel;
		}
	}

	private static class RawRoutineGraph {
		final long id;
		final Map<Integer, Set<RawOpcodeEdge>> opcodeEdges = new HashMap<Integer, Set<RawOpcodeEdge>>();
		final Map<Integer, Set<RawRoutineEdge>> routineEdges = new HashMap<Integer, Set<RawRoutineEdge>>();

		public RawRoutineGraph(long id) {
			this.id = id;
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

	private final Set<Long> preloadedRoutines = new HashSet<Long>();
	private final Map<Long, RawRoutineGraph> rawGraphs = new HashMap<Long, RawRoutineGraph>();

	ScriptRunLoader() {
	}

	private ScriptNode createNode(int opcode, ScriptNode.Type type, int index) {
		if (type == Type.BRANCH) {
			int userLevel = (index >>> 26);
			index = (index & 0x3ffffff);
			return new ScriptBranchNode(opcode, index, userLevel);
		} else {
			return new ScriptNode(type, opcode, index);
		}
	}

	private RawRoutineGraph getRawGraph(Long id) {
		RawRoutineGraph graph = rawGraphs.get(id);
		if (graph == null) {
			graph = new RawRoutineGraph(id);
			rawGraphs.put(id, graph);
		}
		return graph;
	}

	void loadRun(ScriptRunFileSet run, ScriptFlowGraph graph) throws IOException {
		preloadedRoutines.clear();
		for (ScriptRoutineGraph preloadedRoutine : graph.getRoutines())
			preloadedRoutines.add(preloadedRoutine.id);
		rawGraphs.clear();

		loadOpcodeEdges(run);
		loadRoutineEdges(run, graph);
		loadNodes(run, graph);
		linkNodes(graph);
	}

	private void loadOpcodeEdges(ScriptRunFileSet run) throws IOException {
		int unitHash, routineHash, fromIndex, toIndex, userLevel;
		long routineId;
		RawRoutineGraph graph;
		LittleEndianInputStream input = new LittleEndianInputStream(run.opcodeEdgeFile);

		while (input.ready(0x10)) {
			unitHash = input.readInt();
			routineHash = input.readInt();
			routineId = ScriptRoutineGraph.constructId(unitHash, routineHash);

			fromIndex = input.readInt();
			userLevel = (fromIndex >>> 26);
			fromIndex = (fromIndex & 0x3ffffff);
			toIndex = input.readInt();

			graph = getRawGraph(routineId);
			graph.addRawEdge(new RawOpcodeEdge(routineId, fromIndex, toIndex, userLevel));
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
		ScriptRoutineGraph routine, fromRoutine, toRoutine;
		ScriptNode fromNode;
		ScriptBranchNode branchNode;
		for (RawRoutineGraph rawGraph : rawGraphs.values()) {
			for (Set<RawOpcodeEdge> edges : rawGraph.opcodeEdges.values()) {
				for (RawOpcodeEdge edge : edges) {
					routine = graph.getRoutine(edge.routineId);
					fromNode = routine.getNode(edge.fromIndex);

					if (!(fromNode instanceof ScriptBranchNode)) {
						if (!isFallThrough(fromNode, edge)) {
							Log.warn(
									"Exception caught within throwing routine: %d -> %d from opcode 0x%x in routine 0x%x!",
									edge.fromIndex, edge.toIndex, fromNode.opcode, edge.routineId);
							graph.edges.addExceptionEdge(edge.routineId, fromNode, edge.routineId, edge.toIndex,
									edge.userLevel);
						}
						continue;
					}

					branchNode = (ScriptBranchNode) routine.getNode(edge.fromIndex);
					branchNode.setTarget(routine.getNode(edge.toIndex));

					if (routine.getNode(edge.toIndex).index != edge.toIndex) {
						throw new MergeException("Incorrect node index: expected %d but found %d", edge.toIndex,
								routine.getNode(edge.toIndex).index);
					}
				}
			}

			for (Set<RawRoutineEdge> edges : rawGraph.routineEdges.values()) {
				for (RawRoutineEdge edge : edges) {
					fromRoutine = graph.getRoutine(edge.fromRoutineId);
					if (fromRoutine == null) {
						throw new IllegalArgumentException(String.format(
								"Found a routine edge from an unknown routine 0x%x", edge.fromRoutineId));
					}
					fromNode = fromRoutine.getNode(edge.fromIndex);
					toRoutine = graph.getRoutine(edge.toRoutineId);

					if (edge.toIndex == 0)
						graph.edges.addCallEdge(fromRoutine.id, fromNode, toRoutine.id, edge.userLevel);
					else
						graph.edges.addExceptionEdge(fromRoutine.id, fromNode, toRoutine.id, edge.toIndex,
								edge.userLevel);
				}
			}
		}
	}

	private void loadRoutineEdges(ScriptRunFileSet run, ScriptFlowGraph graph) throws IOException {
		int fromUnitHash, fromRoutineHash, fromIndex, toUnitHash, toRoutineHash, toIndex, userLevel;
		long fromRoutineId, toRoutineId;
		RawRoutineGraph routine;
		LittleEndianInputStream input = new LittleEndianInputStream(run.routineEdgeFile);

		while (input.ready(24)) {
			fromUnitHash = input.readInt();
			fromRoutineHash = input.readInt();
			fromIndex = input.readInt();
			userLevel = (fromIndex >>> 26);
			fromIndex = (fromIndex & 0x3ffffff);
			toUnitHash = input.readInt();
			toRoutineHash = input.readInt();
			toIndex = input.readInt();

			fromRoutineId = ScriptRoutineGraph.constructId(fromUnitHash, fromRoutineHash);
			toRoutineId = ScriptRoutineGraph.constructId(toUnitHash, toRoutineHash);

			routine = getRawGraph(fromRoutineId);
			routine.addRawEdge(new RawRoutineEdge(fromRoutineId, fromIndex, toRoutineId, toIndex, userLevel));

			if (ScriptMergeWatchList.getInstance().watch(fromRoutineId, fromIndex)) {
				Log.log("Loaded routine edge 0x%x|0x%x %d -> 0x%x|0x%x", fromUnitHash, fromRoutineHash, fromIndex,
						toUnitHash, toRoutineHash);
			}
		}

		if (input.ready()) {
			Log.error("Input file " + run.routineEdgeFile.getAbsolutePath() + " has trailing data!");
		}
		input.close();
	}

	private void loadNodes(ScriptRunFileSet run, ScriptFlowGraph graph) throws IOException {
		int unitHash, routineHash, opcodeField, opcode, extendedValue, nodeIndex = 0;
		ScriptNode.Type type;
		long routineId;
		ScriptNode node, lastNode = null;
		ScriptRoutineGraph routine = null;
		LittleEndianInputStream input = new LittleEndianInputStream(run.nodeFile);

		while (input.ready(0x10)) {
			unitHash = input.readInt();
			routineHash = input.readInt();
			routineId = ScriptRoutineGraph.constructId(unitHash, routineHash);

			if (routine == null || routine.id != routineId) {
				lastNode = null;
				routine = null;
				routine = graph.getRoutine(routineId);
			}

			if (routine == null) {
				Log.message("Create routine %x|%x", unitHash, routineHash);
				routine = new ScriptRoutineGraph(unitHash, routineHash, graph.isFragmentary);
				graph.addRoutine(routine);
			} else if (preloadedRoutines.contains(routine.id)) { // were nodes copied from the right?
				routine.clearNodes(); // have a copy on the left, so remove copied nodes
				preloadedRoutines.remove(routine.id);
			}

			opcodeField = input.readInt();
			opcode = opcodeField & 0xff;
			extendedValue = (opcodeField >> 8) & 0xff;
			type = ScriptNode.identifyType(opcode, extendedValue);

			// parse out extended value for include/eval nodes
			nodeIndex = input.readInt();
			node = createNode(opcode, type, nodeIndex);
			if (lastNode != null)
				lastNode.setNext(node);
			lastNode = node;

			Log.message("%s: @%d Opcode 0x%x (%x|%x) [%s]", getClass().getSimpleName(), nodeIndex, opcode, unitHash,
					routineHash, node.type);

			routine.addNode(node);
		}

		if (input.ready())
			Log.error("Input file " + run.nodeFile.getAbsolutePath() + " has trailing data!");
		input.close();
	}
}
