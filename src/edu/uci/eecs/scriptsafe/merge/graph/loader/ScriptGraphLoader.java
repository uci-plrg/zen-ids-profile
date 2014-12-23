package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.IOException;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptGraphLoader {

	public ScriptGraphLoader() {
	}

	private ScriptNode createNode(int opcode) {
		switch (opcode) {
			default:
				return new ScriptNode(opcode);
		}
	}

	public ScriptFlowGraph loadGraph(ScriptRunFileSet files) throws IOException {
		ScriptFlowGraph graph = new ScriptFlowGraph();
		loadNodes(files, graph);
		return graph;
	}

	private void loadNodes(ScriptRunFileSet files, ScriptFlowGraph graph) throws IOException {
		int unitHash, routineHash, opcode;
		long routineId, currentRoutineId = 0L;
		ScriptNode node, lastNode = null;
		ScriptRoutineGraph routine = null;
		LittleEndianInputStream input = new LittleEndianInputStream(files.nodeFile);

		while (input.ready(16)) {
			unitHash = input.readInt();
			routineHash = input.readInt();
			routineId = ScriptRoutineGraph.constructId(unitHash, routineHash);

			if (routineId != currentRoutineId) {
				routine = graph.getRoutine(routineId);
				if (routine == null) {
					routine = new ScriptRoutineGraph(unitHash, routineHash);
					graph.addRoutine(routine);
					currentRoutineId = routineId;
				} else {
					Log.log("Warning: Routine graphs are interleaved in %s", files.nodeFile.getAbsolutePath());
				}
			}

			opcode = input.readInt();
			node = createNode(opcode);
			if (lastNode != null)
				lastNode.setNext(node);
			lastNode = node;

			input.readInt(); // skip empty dword

			routine.addNode(node);
		}

		if (input.ready())
			throw new IllegalArgumentException("Input file " + files.nodeFile.getAbsolutePath() + " has trailing data!");
	}
}
