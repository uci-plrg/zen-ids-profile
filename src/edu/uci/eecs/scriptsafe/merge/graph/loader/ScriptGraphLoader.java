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

	public ScriptFlowGraph loadGraph(ScriptGraphFile file) throws IOException {
		int unitHash, routineHash, opcode;
		long routineId;
		ScriptNode node, lastNode = null;
		ScriptRoutineGraph routine;
		ScriptFlowGraph graph = new ScriptFlowGraph();
		LittleEndianInputStream input = new LittleEndianInputStream(file.file);
		
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
			node = createNode(opcode);
			if (lastNode != null)
				lastNode.setNext(node);
			lastNode = node;

			routine.addNode(node);
		}

		//if (input.ready())
	//		throw new IllegalArgumentException("Input file " + file.file.getAbsolutePath() + " has trailing data!");

		return graph;
	}
}
