package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;

public class ScriptGraphCloner {

	private final Map<ScriptBranchNode, ScriptBranchNode> branchNodeCopies = new HashMap<ScriptBranchNode, ScriptBranchNode>();
	private final Map<ScriptCallNode, ScriptCallNode> callNodeCopies = new HashMap<ScriptCallNode, ScriptCallNode>();
	private final Map<ScriptEvalNode, ScriptEvalNode> evalNodeCopies = new HashMap<ScriptEvalNode, ScriptEvalNode>();

	public ScriptFlowGraph deepCopy(ScriptFlowGraph original) {
		branchNodeCopies.clear();
		callNodeCopies.clear();
		evalNodeCopies.clear();
		
		ScriptFlowGraph flowCopy = new ScriptFlowGraph();
		deepCopy(original, flowCopy);
		return flowCopy;
	}

	private void deepCopy(ScriptRoutineGraph routineOriginal, ScriptRoutineGraph routineCopy) {
		branchNodeCopies.clear();

		// First: shallow copy
		for (int i = 0; i < routineOriginal.getNodeCount(); i++) {
			ScriptNode nodeOriginal = routineOriginal.getNode(i);
			ScriptNode nodeCopy = nodeOriginal.copy();
			routineCopy.addNode(nodeCopy);
			switch (nodeCopy.type) {
				case BRANCH:
					branchNodeCopies.put((ScriptBranchNode) nodeCopy, (ScriptBranchNode) nodeOriginal);
					break;
				case CALL:
					callNodeCopies.put((ScriptCallNode) nodeCopy, (ScriptCallNode) nodeOriginal);
					break;
				case EVAL:
					evalNodeCopies.put((ScriptEvalNode) nodeCopy, (ScriptEvalNode) nodeOriginal);
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
	}

	private void deepCopy(ScriptFlowGraph original, ScriptFlowGraph flowCopy) {

		for (ScriptRoutineGraph routine : original.getRoutines()) {
			ScriptRoutineGraph routineCopy = routine.copy();
			deepCopy(routine, routineCopy);
			flowCopy.addRoutine(routineCopy);
		}
		Log.log("Copying %d eval proxies", original.getEvalProxyCount());
		for (ScriptRoutineGraphProxy proxy : original.getEvalProxies()) {
			ScriptRoutineGraph copyProxyTarget = proxy.getTarget();
			ScriptRoutineGraph routineCopy = copyProxyTarget.copy();
			deepCopy(copyProxyTarget, routineCopy);
			flowCopy.addRoutine(routineCopy);
		}
		Log.log("Copy now has %d eval proxies", flowCopy.getEvalProxyCount());

		// Third: link call and eval targets in copy
		for (Map.Entry<ScriptCallNode, ScriptCallNode> entry : callNodeCopies.entrySet()) {
			ScriptCallNode callCopy = entry.getKey();
			ScriptCallNode callOriginal = entry.getValue();
			for (ScriptRoutineGraph targetOriginal : callOriginal.targets.values()) {
				callCopy.addTarget(flowCopy.getRoutine(targetOriginal.id));
			}
		}
		for (Map.Entry<ScriptEvalNode, ScriptEvalNode> entry : evalNodeCopies.entrySet()) {
			ScriptEvalNode evalCopy = entry.getKey();
			ScriptEvalNode evalOriginal = entry.getValue();
			for (ScriptRoutineGraphProxy targetOriginal : evalOriginal.getTargets()) {
				evalCopy.addTarget(flowCopy.getEvalProxy(targetOriginal.getEvalId()));
			}
		}
	}
}
