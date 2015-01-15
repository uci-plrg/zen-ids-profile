package edu.uci.eecs.scriptsafe.merge.graph;

import java.util.HashMap;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;

public class ScriptGraphCloner {

	private final Map<ScriptNode, ScriptNode> nodeCopies = new HashMap<ScriptNode, ScriptNode>();
	private final Map<ScriptBranchNode, ScriptBranchNode> branchNodeCopies = new HashMap<ScriptBranchNode, ScriptBranchNode>();
	private final Map<ScriptCallNode, ScriptCallNode> callNodeCopies = new HashMap<ScriptCallNode, ScriptCallNode>();
	private final Map<ScriptEvalNode, ScriptEvalNode> evalNodeCopies = new HashMap<ScriptEvalNode, ScriptEvalNode>();
	private ScriptFlowGraph flowCopy;

	public ScriptFlowGraph deepCopy(ScriptFlowGraph original) {
		branchNodeCopies.clear();
		callNodeCopies.clear();
		evalNodeCopies.clear();

		flowCopy = new ScriptFlowGraph("Deep copy [" + original.description + "]");
		deepCopy(original);
		return flowCopy;
	}

	public ScriptFlowGraph copyRoutines(ScriptFlowGraph original, ScriptFlowGraph flowCopy) {
		branchNodeCopies.clear();
		callNodeCopies.clear();
		evalNodeCopies.clear();

		for (ScriptRoutineGraph routine : original.getRoutines()) {
			ScriptRoutineGraph routineCopy = routine.copy();
			shallowCopy(routine, routineCopy);
			flowCopy.addRoutine(routineCopy);
		}

		Log.log("Copying %d dynamic routine proxies", original.getDynamicRoutineProxyCount());
		for (RoutineEdge proxy : original.getDynamicRoutineProxies()) {
			ScriptRoutineGraph copyProxyTarget = proxy.getTargetRoutine();
			ScriptRoutineGraph routineCopy = copyProxyTarget.copy();
			shallowCopy(copyProxyTarget, routineCopy);
			flowCopy.addRoutine(routineCopy);
		}
		Log.log("Copy now has %d dynamic routine proxies", flowCopy.getDynamicRoutineProxyCount());

		return flowCopy;
	}

	private void shallowCopy(ScriptRoutineGraph routineOriginal, ScriptRoutineGraph routineCopy) {
		for (int i = 0; i < routineOriginal.getNodeCount(); i++) {
			ScriptNode nodeOriginal = routineOriginal.getNode(i);
			ScriptNode nodeCopy = nodeOriginal.copy();
			routineCopy.addNode(nodeCopy);
		}
	}

	private void deepCopy(ScriptRoutineGraph routineOriginal, ScriptRoutineGraph routineCopy) {
		branchNodeCopies.clear();

		// First: shallow copy
		for (int i = 0; i < routineOriginal.getNodeCount(); i++) {
			ScriptNode nodeOriginal = routineOriginal.getNode(i);
			ScriptNode nodeCopy = nodeOriginal.copy();
			routineCopy.addNode(nodeCopy);
			nodeCopies.put(nodeCopy, nodeOriginal);
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

	private void deepCopy(ScriptFlowGraph original) {

		for (ScriptRoutineGraph routine : original.getRoutines()) {
			ScriptRoutineGraph routineCopy = routine.copy();
			deepCopy(routine, routineCopy);
			flowCopy.addRoutine(routineCopy);
		}
		Log.log("Copying %d eval proxies from %s to %s", original.getDynamicRoutineProxyCount(), original.description,
				flowCopy.description);
		for (RoutineEdge proxy : original.getDynamicRoutineProxies()) {
			ScriptRoutineGraph copyProxyTarget = proxy.getTargetRoutine();
			ScriptRoutineGraph routineCopy = copyProxyTarget.copy();
			deepCopy(copyProxyTarget, routineCopy);
			flowCopy.addRoutine(routineCopy);
		}
		Log.log("Copy now has %d eval proxies", flowCopy.getDynamicRoutineProxyCount());

		for (Map.Entry<ScriptNode, ScriptNode> entry : nodeCopies.entrySet()) {
			ScriptNode nodeCopy = entry.getKey();
			ScriptNode nodeOriginal = entry.getValue();
			for (RoutineExceptionEdge throwEdge : nodeOriginal.getThrownExceptions()) {
				nodeCopy.addThrownException(new RoutineExceptionEdge(
						flowCopy.getRoutine(throwEdge.getTargetRoutine().id), throwEdge.getTargetIndex()));
			}
		}

		// Third: link call and eval targets in copy
		for (Map.Entry<ScriptCallNode, ScriptCallNode> entry : callNodeCopies.entrySet()) {
			ScriptCallNode callCopy = entry.getKey();
			ScriptCallNode callOriginal = entry.getValue();
			for (ScriptRoutineGraph targetOriginal : callOriginal.staticTargets.values()) {
				callCopy.addStaticTarget(flowCopy.getRoutine(targetOriginal.id));
			}
			for (RoutineEdge targetOriginal : callOriginal.getDynamicTargets()) {
				callCopy.addDynamicTarget(flowCopy.getDynamicRoutineProxy(targetOriginal.getDynamicRoutineId()));
			}
		}
		for (Map.Entry<ScriptEvalNode, ScriptEvalNode> entry : evalNodeCopies.entrySet()) {
			ScriptEvalNode evalCopy = entry.getKey();
			ScriptEvalNode evalOriginal = entry.getValue();
			for (RoutineEdge targetOriginal : evalOriginal.getTargets()) {
				evalCopy.addTarget(flowCopy.getDynamicRoutineProxy(targetOriginal.getDynamicRoutineId()));
			}
		}
	}
}
