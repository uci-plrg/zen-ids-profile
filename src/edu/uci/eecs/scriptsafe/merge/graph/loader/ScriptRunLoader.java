package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

class ScriptRunLoader {

	private static class RawOpcodeEdge {
		final long routineId;
		final int fromIndex;
		final int toIndex;

		public RawOpcodeEdge(long routineId, int fromIndex, int toIndex) {
			super();
			this.routineId = routineId;
			this.fromIndex = fromIndex;
			this.toIndex = toIndex;
		}
	}

	private static class RawRoutineGraph {
		final long id;
		final Map<Integer, Set<RawOpcodeEdge>> opcodeEdges = new HashMap<Integer, Set<RawOpcodeEdge>>();

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
	}

	private ScriptNode createNode(int opcode, int index, long routineId) {
		RawRoutineGraph graph = getRawGraph(routineId);
		if (graph != null) {
			Set<RawOpcodeEdge> edges = graph.opcodeEdges.get(index);
			if (edges != null && edges.size() > 1)
				return new ScriptBranchNode(opcode, index);
		}
		return new ScriptNode(opcode, index);
	}

	private final Map<Long, RawRoutineGraph> rawGraphs = new HashMap<Long, RawRoutineGraph>();

	ScriptRunLoader() {
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
		loadNodes(run, graph);
	}

	private void loadOpcodeEdges(ScriptRunFileSet run) throws IOException {
		int unitHash, routineHash, fromIndex, toIndex;
		long routineId;
		RawRoutineGraph graph;
		LittleEndianInputStream input = new LittleEndianInputStream(run.nodeFile);

		while (input.ready(16)) {
			unitHash = input.readInt();
			routineHash = input.readInt();
			routineId = ScriptRoutineGraph.constructId(unitHash, routineHash);

			fromIndex = input.readInt();
			toIndex = input.readInt();

			graph = getRawGraph(routineId);
			graph.addRawEdge(new RawOpcodeEdge(routineId, fromIndex, toIndex));
		}
	}

	private void loadNodes(ScriptRunFileSet run, ScriptFlowGraph graph) throws IOException {
		int unitHash, routineHash, opcode, nodeIndex = 0, currentUnitHash = 0;
		long routineId;
		ScriptNode node, lastNode = null;
		ScriptRoutineGraph routine = null;
		Set<Integer> visitedUnits = new HashSet<Integer>();
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

			// maintain multiple node indexes, one for each routine in the current unit
			
			if (unitHash != currentUnitHash) {
				currentUnitHash = unitHash;
				if (visitedUnits.contains(unitHash)) {
					// omit repeats in script-cfi!
					Log.log("Warning: Routine graphs are interleaved in %s", run.nodeFile.getAbsolutePath());
				} else {
					visitedUnits.add(currentUnitHash);
				}
			}

			opcode = input.readInt();
			node = createNode(opcode, nodeIndex, currentUnitHash);
			if (lastNode != null)
				lastNode.setNext(node);
			lastNode = node;

			Log.log("Opcode %x (%x|%x): %s", opcode, unitHash, routineHash, node.getClass().getSimpleName());

			input.readInt(); // skip empty dword

			routine.addNode(node);
		}

		if (input.ready())
			throw new IllegalArgumentException("Input file " + run.nodeFile.getAbsolutePath() + " has trailing data!");
	}
}
