package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptCallNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptEvalNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraphProxy;

class ScriptRunLoader {

	private static class RawOpcodeEdge {
		final long routineId;
		final int fromIndex;
		final int toIndex;

		public RawOpcodeEdge(long routineId, int fromIndex, int toIndex) {
			this.routineId = routineId;
			this.fromIndex = fromIndex;
			this.toIndex = toIndex;
		}
	}

	private static class RawRoutineEdge {
		final long fromRoutineId;
		final int fromIndex;
		final long toRoutineId;

		public RawRoutineEdge(long fromRoutineId, int fromIndex, long toRoutineId) {
			this.fromRoutineId = fromRoutineId;
			this.fromIndex = fromIndex;
			this.toRoutineId = toRoutineId;
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

	private final Map<Long, RawRoutineGraph> rawGraphs = new HashMap<Long, RawRoutineGraph>();

	ScriptRunLoader() {
	}

	private ScriptNode createNode(int opcode, int index, long routineId) {
		RawRoutineGraph graph = getRawGraph(routineId);
		if (graph != null) {
			Set<RawOpcodeEdge> opcodeEdges = graph.opcodeEdges.get(index);

			if (opcodeEdges != null)
				Log.log("Found %d opcode edges for node with index %d", opcodeEdges.size(), index);

			Set<RawRoutineEdge> routineEdges = graph.routineEdges.get(index);
			if (opcodeEdges != null && opcodeEdges.size() > 1 && routineEdges != null)
				throw new IllegalStateException(String.format(
						"Node %d in routine %x has both opcode edges and routine edges!", index, routineId));
			if (opcodeEdges != null && opcodeEdges.size() > 1)
				return new ScriptBranchNode(opcode, index);
			if (routineEdges != null) {
				if (ScriptRoutineGraph.isEval(routineEdges.iterator().next().toRoutineId))
					return new ScriptEvalNode(opcode, index);
				else
					return new ScriptCallNode(opcode, index);
			}
		}
		return new ScriptNode(opcode, index);
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
		loadOpcodeEdges(run);
		loadRoutineEdges(run, graph);
		loadNodes(run, graph);
		linkNodes(graph);
	}

	private void loadOpcodeEdges(ScriptRunFileSet run) throws IOException {
		int unitHash, routineHash, fromIndex, toIndex;
		long routineId;
		RawRoutineGraph graph;
		LittleEndianInputStream input = new LittleEndianInputStream(run.opcodeEdgeFile);

		while (input.ready(16)) {
			unitHash = input.readInt();
			routineHash = input.readInt();
			routineId = ScriptRoutineGraph.constructId(unitHash, routineHash);

			fromIndex = input.readInt();
			toIndex = input.readInt();

			Log.log("Raw opcode edge from %d to %d", fromIndex, toIndex);

			graph = getRawGraph(routineId);
			graph.addRawEdge(new RawOpcodeEdge(routineId, fromIndex, toIndex));
		}
	}

	private void linkNodes(ScriptFlowGraph graph) {
		ScriptRoutineGraph routine, fromRoutine, toRoutine;
		ScriptRoutineGraphProxy toRoutineProxy;
		ScriptNode fromNode;
		ScriptBranchNode branchNode;
		ScriptCallNode callSite;
		ScriptEvalNode evalSite;
		for (RawRoutineGraph rawGraph : rawGraphs.values()) {
			for (Set<RawOpcodeEdge> edges : rawGraph.opcodeEdges.values()) {
				if (edges.size() == 1)
					continue;
				for (RawOpcodeEdge edge : edges) {
					routine = graph.getRoutine(edge.routineId);
					fromNode = routine.getNode(edge.fromIndex);

					if (!(fromNode instanceof ScriptBranchNode)) {
						throw new IllegalArgumentException(String.format(
								"Branch from non-branch node with opcode %d at index %d in routine 0x%x!",
								fromNode.opcode, edge.fromIndex, edge.routineId));
					}

					branchNode = (ScriptBranchNode) routine.getNode(edge.fromIndex);
					branchNode.setTarget(routine.getNode(edge.toIndex));
				}
			}

			for (Set<RawRoutineEdge> edges : rawGraph.routineEdges.values()) {
				for (RawRoutineEdge edge : edges) {
					fromRoutine = graph.getRoutine(edge.fromRoutineId);
					if (fromRoutine == null) {
						throw new IllegalArgumentException(String.format(
								"Found a routine edge from an unknown routine 0x%x", edge.fromRoutineId));
					}

					if (ScriptRoutineGraph.isEval(edge.toRoutineId)) {
						toRoutineProxy = graph.getEvalProxy(ScriptRoutineGraph.getEvalId(edge.toRoutineId));

						evalSite = (ScriptEvalNode) fromRoutine.getNode(edge.fromIndex);
						evalSite.addTarget(toRoutineProxy);
					} else {
						toRoutine = graph.getRoutine(edge.toRoutineId);

						callSite = (ScriptCallNode) fromRoutine.getNode(edge.fromIndex);
						callSite.addTarget(toRoutine);
					}
				}
			}
		}
	}

	private void loadRoutineEdges(ScriptRunFileSet run, ScriptFlowGraph graph) throws IOException {
		int fromUnitHash, fromRoutineHash, fromIndex, toUnitHash, toRoutineHash;
		long fromRoutineId, toRoutineId;
		RawRoutineGraph routine;
		LittleEndianInputStream input = new LittleEndianInputStream(run.routineEdgeFile);

		while (input.ready(24)) {
			fromUnitHash = input.readInt();
			fromRoutineHash = input.readInt();
			fromIndex = input.readInt();
			toUnitHash = input.readInt();
			toRoutineHash = input.readInt();
			input.readInt(); // toIndex is always 0

			fromRoutineId = ScriptRoutineGraph.constructId(fromUnitHash, fromRoutineHash);
			toRoutineId = ScriptRoutineGraph.constructId(toUnitHash, toRoutineHash);

			/*
			 * if (graph.getRoutine(fromRoutineId) == null) throw new
			 * IllegalArgumentException(String.format("Found a routine edge from an unknown routine 0x%x",
			 * fromRoutineId)); if (graph.getRoutine(toRoutineId) == null) throw new
			 * IllegalArgumentException(String.format("Found a routine edge to an unknown routine 0x%x", toRoutineId));
			 */

			routine = getRawGraph(fromRoutineId);
			routine.addRawEdge(new RawRoutineEdge(fromRoutineId, fromIndex, toRoutineId));
		}
	}

	private void loadNodes(ScriptRunFileSet run, ScriptFlowGraph graph) throws IOException {
		int unitHash, routineHash, opcode, nodeIndex = 0;
		long routineId;
		ScriptNode node, lastNode = null;
		ScriptRoutineGraph routine = null;
		LittleEndianInputStream input = new LittleEndianInputStream(run.nodeFile);

		while (input.ready(16)) {
			unitHash = input.readInt();
			routineHash = input.readInt();
			routineId = ScriptRoutineGraph.constructId(unitHash, routineHash);

			routine = graph.getRoutine(routineId);
			if (routine == null) {
				routine = new ScriptRoutineGraph(unitHash, routineHash);
				graph.addRoutine(routine);
			}

			opcode = input.readInt();
			nodeIndex = input.readInt();
			node = createNode(opcode, nodeIndex, routineId);
			if (lastNode != null)
				lastNode.setNext(node);
			lastNode = node;

			Log.log("Opcode %x (%x|%x): %s", opcode, unitHash, routineHash, node.getClass().getSimpleName());

			routine.addNode(node);
		}

		if (input.ready())
			throw new IllegalArgumentException("Input file " + run.nodeFile.getAbsolutePath() + " has trailing data!");
	}
}
