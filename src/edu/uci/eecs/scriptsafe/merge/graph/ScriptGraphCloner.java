package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

public class ScriptGraphCloner {

	private final Map<ScriptBranchNode, ScriptBranchNode> branchNodeCopies = new HashMap<ScriptBranchNode, ScriptBranchNode>();
	private final Map<ScriptCallNode, ScriptCallNode> callNodeCopies = new HashMap<ScriptCallNode, ScriptCallNode>();

	public ScriptFlowGraph deepCopy(ScriptFlowGraph original) {
		ScriptFlowGraph flowCopy = new ScriptFlowGraph();

		for (ScriptRoutineGraph routine : original.routines.values()) {
			branchNodeCopies.clear();
			ScriptRoutineGraph routineCopy = routine.copy();
			
			// First: shallow copy
			for (int i = 0; i < routine.getNodeCount(); i++) {
				ScriptNode nodeOriginal = routine.getNode(i);
				ScriptNode nodeCopy = nodeOriginal.copy();
				routineCopy.addNode(nodeCopy);
				switch (nodeCopy.type) {
					case BRANCH:
						branchNodeCopies.put((ScriptBranchNode) nodeCopy, (ScriptBranchNode) nodeOriginal);
						break;
					case CALL:
						callNodeCopies.put((ScriptCallNode) nodeCopy, (ScriptCallNode) nodeOriginal);
						break;
				}
			}
			
			// Second: link branch targets in copy 
			for (Map.Entry<ScriptBranchNode, ScriptBranchNode> entry : branchNodeCopies.entrySet()) {
				ScriptBranchNode branchCopy = entry.getKey();
				ScriptBranchNode branchOriginal = entry.getValue();

				ScriptNode target = branchOriginal.getTarget();
				if (target != null)
					branchCopy.setTarget(routineCopy.getNode(target.index));
			}
			flowCopy.routines.put(routine.id, routineCopy);
		}

		// Third: link call targets in copy
		for (Map.Entry<ScriptCallNode, ScriptCallNode> entry : callNodeCopies.entrySet()) {
			ScriptCallNode callCopy = entry.getKey();
			ScriptCallNode callOriginal = entry.getValue();
			for (ScriptRoutineGraph targetOriginal : callOriginal.targets.values()) {
				callCopy.addTarget(flowCopy.getRoutine(targetOriginal.id));
			}
		}
		
		return flowCopy;
	}
}
