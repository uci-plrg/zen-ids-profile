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
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

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
			Set<RawRoutineEdge> routineEdges = graph.routineEdges.get(index);
			if (opcodeEdges != null && opcodeEdges.size() > 1 && routineEdges != null)
				throw new IllegalStateException(String.format(
						"Node %d in routine %x has both opcode edges and routine edges!", index, routineId));
			if (opcodeEdges != null && opcodeEdges.size() > 1)
				return new ScriptBranchNode(opcode, index);
			if (routineEdges != null)
				return new ScriptCallNode(opcode, index);
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
		loadRoutineEdges(run);
		loadNodes(run, graph);
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

	private void loadRoutineEdges(ScriptRunFileSet run) throws IOException {
		int fromUnitHash, fromRoutineHash, fromIndex, toUnitHash, toRoutineHash;
		long fromRoutineId, toRoutineId;
		RawRoutineGraph graph;
		LittleEndianInputStream input = new LittleEndianInputStream(run.routineEdgeFile);

		while (input.ready(24)) {
			fromUnitHash = input.readInt();
			fromRoutineHash = input.readInt();
			fromIndex = input.readInt();
			toUnitHash = input.readInt();
			toRoutineHash = input.readInt();

			fromRoutineId = ScriptRoutineGraph.constructId(fromUnitHash, fromRoutineHash);
			toRoutineId = ScriptRoutineGraph.constructId(toUnitHash, toRoutineHash);

			graph = getRawGraph(fromRoutineId);
			graph.addRawEdge(new RawRoutineEdge(fromRoutineId, fromIndex, toRoutineId));
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
