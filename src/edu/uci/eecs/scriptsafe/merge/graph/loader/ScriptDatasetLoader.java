package edu.uci.eecs.scriptsafe.merge.graph.loader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.io.LittleEndianInputStream;
import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.MergeException;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineId;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode.Type;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptDatasetLoader {

	private static class PendingEdges<NodeType extends ScriptNode, TargetType> {
		final long fromRoutineId;
		final NodeType fromNode;
		final TargetType target;

		PendingEdges(long fromRoutineId, NodeType fromNode, TargetType target) {
			this.fromRoutineId = fromRoutineId;
			this.fromNode = fromNode;
			this.target = target;
		}
	}

	private final List<PendingEdges<ScriptBranchNode, Integer>> pendingBranches = new ArrayList<PendingEdges<ScriptBranchNode, Integer>>();
	private final List<ScriptNode> calls = new ArrayList<ScriptNode>();

	private LittleEndianInputStream in;

	private boolean shallow;

	public void loadDataset(File datasetFile, File routineCatalog, ScriptFlowGraph graph, boolean shallow)
			throws IOException {
		RoutineId.Cache.INSTANCE.load(routineCatalog);
		in = new LittleEndianInputStream(datasetFile);
		this.shallow = shallow;

		in.readInt(); // skip hashtable pointer
		int routineCount = in.readInt();
		int dynamicRoutineCount = in.readInt();

		for (int i = 0; i < routineCount; i++)
			graph.addRoutine(loadNextRoutine(graph));
		for (int i = 0; i < dynamicRoutineCount; i++)
			graph.appendDynamicRoutine(loadNextRoutine(graph));

		in.close();
	}

	private ScriptRoutineGraph loadNextRoutine(ScriptFlowGraph graph) throws IOException {
		int routineHash = in.readInt();
		int dynamicRoutineId, dynamicRoutineCount, targetNodeIndex, userLevel;
		ScriptRoutineGraph routine = new ScriptRoutineGraph(routineHash, RoutineId.Cache.INSTANCE.getId(routineHash),
				false);

		int nodeCount = in.readInt();
		ScriptNode node, previousNode = null;
		for (int i = 0; i < nodeCount; i++) {
			int nodeId = in.readInt();
			int opcode = nodeId & 0xff;
			int typeOrdinal = (nodeId >> 8) & 0xff;
			int lineNumber = (nodeId >> 0x10);
			ScriptNode.Type type = ScriptNode.Type.values()[typeOrdinal];
			int target = in.readInt();
			userLevel = shallow ? 0 : (target >>> 26);
			if (type == Type.BRANCH) {
				target = (target & 0x3ffffff);
				ScriptBranchNode branch = new ScriptBranchNode(routineHash, opcode, i, lineNumber,
						ScriptNode.USER_LEVEL_TOP);
				branch.setNodeUserLevel(userLevel);
				pendingBranches.add(new PendingEdges<ScriptBranchNode, Integer>(routineHash, branch, target));
				routine.addNode(branch);
				node = branch;
			} else {
				node = new ScriptNode(routineHash, type, opcode, lineNumber, i);
				if (type == Type.CALL || type == Type.EVAL)
					calls.add(node); // use list seequence instead of `target` pointer
				node.setNodeUserLevel(userLevel);
				routine.addNode(node);
			}
			
			if (previousNode != null) 
				previousNode.setNext(node);
			previousNode = node;
				
			Log.message("%s: @%d Opcode 0x%x (%x) [%s]", getClass().getSimpleName(), i, opcode, routineHash, type);
			if (ScriptMergeWatchList.watch(routineHash))
				Log.log("%s: @%d Opcode 0x%x (%x) [%s]", getClass().getSimpleName(), i, opcode, routineHash, type);
		}

		for (PendingEdges<ScriptBranchNode, Integer> pendingBranch : pendingBranches) {
			switch (ScriptNode.Opcode.forCode(pendingBranch.fromNode.opcode).targetType) {
				case DYNAMIC:
				case NULLABLE:
					if (pendingBranch.target == ScriptBranchNode.UNASSIGNED_BRANCH_TARGET_ID) {
						pendingBranch.fromNode.setTarget(null);
						break;
					}
				case REQUIRED:
					ScriptNode targetNode = routine.getNode(pendingBranch.target);
					pendingBranch.fromNode.setTarget(targetNode);
					pendingBranch.fromNode.setBranchUserLevel(targetNode.getNodeUserLevel()); // approximately...
					break;
				default:
					throw new MergeException("Illegal opcode for branch node 0x%x", pendingBranch.fromNode.opcode);
			}
		}

		for (ScriptNode call : calls) {
			switch (call.type) {
				case CALL: {
					int callCount = in.readInt();
					for (int i = 0; i < callCount; i++) {
						routineHash = in.readInt();
						targetNodeIndex = in.readInt();
						userLevel = (targetNodeIndex >>> 26);
						targetNodeIndex = (targetNodeIndex & 0x3ffffff);

						if (!shallow) {
							if (ScriptMergeWatchList.watchAny(routine.hash, call.index)
									|| ScriptMergeWatchList.watch(routineHash)) {
								Log.log("Loader added routine edge to the dataset graph: 0x%x %d -%s-> 0x%x",
										routine.hash, call.index, RoutineEdge.printUserLevel(userLevel), routineHash);
							}

							if (targetNodeIndex == 0) {
								graph.edges.addCallEdge(routine.hash, call, routineHash, userLevel);
							} else {
								graph.edges.addExceptionEdge(routine.hash, call, routineHash, targetNodeIndex,
										userLevel);
							}
						}
					}
				}
					break;
				case EVAL: {
					dynamicRoutineCount = in.readInt();
					for (int i = 0; i < dynamicRoutineCount; i++) {
						dynamicRoutineId = in.readInt();
						targetNodeIndex = in.readInt();
						userLevel = (targetNodeIndex >>> 26);
						targetNodeIndex = (targetNodeIndex & 0x3ffffff);

						if (!shallow) {
							if (targetNodeIndex == 0) {
								graph.edges.addCallEdge(routine.hash, call,
										ScriptRoutineGraph.constructDynamicHash(dynamicRoutineId), userLevel);
							} else {
								graph.edges.addExceptionEdge(routine.hash, call,
										ScriptRoutineGraph.constructDynamicHash(dynamicRoutineId), targetNodeIndex,
										userLevel);
							}
						}
					}
				}
					break;
			}
		}

		calls.clear();
		pendingBranches.clear();

		return routine;
	}
}
