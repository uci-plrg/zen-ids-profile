package edu.uci.eecs.scriptsafe.merge;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.analysis.request.RequestFileSet;
import edu.uci.eecs.scriptsafe.analysis.request.RequestSequenceLoader;
import edu.uci.eecs.scriptsafe.merge.graph.GraphEdgeSet;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineExceptionEdge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.GraphEdgeSet.LowerUserLevelResult;
import edu.uci.eecs.scriptsafe.merge.graph.GraphEdgeSet.NewEdgeResult;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;

public class RequestSequenceMerge implements ScriptDatasetGenerator.DataSource, RequestSequenceLoader.RequestCollection {

	private int skipRequestCount, mergeRequestCount, evaluationCount = 0, requestIndex;
	private RequestFileSet requestFiles;
	ScriptFlowGraph leftGraph, rightGraph;

	private final Map<Integer, ScriptRoutineGraph> mergedStaticRoutines = new HashMap<Integer, ScriptRoutineGraph>();
	// private final DynamicRoutineMerge dynamicRoutineMerge; // TODO: dynamic routines
	private final GraphEdgeSet mergedEdges = new GraphEdgeSet();

	private int currentRequestId = -1;

	public RequestSequenceMerge(int skipRequestCount, int mergeRequestCount, RequestFileSet requestFiles,
			ScriptFlowGraph leftGraph, ScriptFlowGraph rightGraph) {
		this.skipRequestCount = skipRequestCount;
		this.mergeRequestCount = mergeRequestCount;
		this.requestFiles = requestFiles;
		this.leftGraph = leftGraph;
		this.rightGraph = rightGraph;

		if (rightGraph.dataSourceType == Type.DATASET) {
			for (ScriptRoutineGraph rightRoutine : rightGraph.getRoutines()) {
				if (ScriptRoutineGraph.isDynamicRoutine(rightRoutine.hash))
					Log.warn("Warning: %s does not support dynamic routines yet! Skipping routine.", getClass()
							.getSimpleName());
				else
					mergedStaticRoutines.put(rightRoutine.hash, rightRoutine);
			}
			for (List<RoutineEdge> edges : rightGraph.edges.getOutgoingEdges()) {
				for (RoutineEdge edge : edges) {
					ScriptNode fromNode = rightGraph.getRoutine(edge.getFromRoutineHash()).getNode(
							edge.getFromRoutineIndex());
					if (edge.getEntryType() == RoutineEdge.Type.THROW) {
						mergedEdges.addExceptionEdge(edge.getFromRoutineHash(), fromNode, edge.getToRoutineHash(),
								((RoutineExceptionEdge) edge).getToRoutineIndex(), edge.getUserLevel());
					} else {
						mergedEdges.addCallEdge(edge.getFromRoutineHash(), fromNode, edge.getToRoutineHash(),
								edge.getUserLevel());
					}
				}
			}
		}
	}

	public void merge() throws IOException {
		requestIndex = 0;
		RequestSequenceLoader.load(requestFiles, this);
	}

	@Override
	public int getDynamicRoutineCount() {
		return 0;
	}

	@Override
	public int getStaticRoutineCount() {
		return mergedStaticRoutines.size();
	}

	@Override
	public Iterable<ScriptRoutineGraph> getDynamicRoutines() {
		return Collections.emptyList();
	}

	@Override
	public Iterable<ScriptRoutineGraph> getStaticRoutines() {
		return mergedStaticRoutines.values();
	}

	@Override
	public int getOutgoingEdgeCount(ScriptNode node) {
		return mergedEdges.getOutgoingEdgeCount(node);
	}

	@Override
	public Iterable<RoutineEdge> getOutgoingEdges(ScriptNode node) {
		return mergedEdges.getOutgoingEdges(node);
	}

	@Override
	public ScriptRoutineGraph createRoutine(int routineHash) {
		throw new MergeException("Cannot create routines for a %s", getClass().getSimpleName());
	}

	@Override
	public ScriptRoutineGraph getRoutine(int routineHash) {
		return mergedStaticRoutines.get(routineHash);
	}

	@Override
	public boolean startRequest(int requestId, File routineCatalog) {
		if (skipRequestCount > 0) {
			skipRequestCount--;
		} else if (mergeRequestCount > 0) {
			mergeRequestCount--;
		} else {
			evaluationCount++;
		}

		currentRequestId = requestId;

		return true;
	}

	@Override
	public boolean addEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, int toIndex, int userLevel,
			File routineCatalog) throws NumberFormatException, IOException {
		ScriptRoutineGraph fromGraph = getRoutineGraph(fromRoutineHash), toGraph;
		if (fromGraph == null) {
			Log.error("Error: request edge 0x%x(%d) -%s-> 0x%x(%d) from unknown routine", fromRoutineHash, fromIndex,
					RoutineEdge.printUserLevel(userLevel), toRoutineHash, toIndex);
			return true;
		}
		ScriptNode fromNode = fromGraph.getNode(fromIndex);
		if (fromRoutineHash == toRoutineHash) {
			toGraph = fromGraph;

			if (fromNode instanceof ScriptBranchNode) {
				ScriptBranchNode branchNode = (ScriptBranchNode) fromNode;
				if (branchNode.getTargetIndex() != toIndex) {
					Log.error("Error: branch node 0x%x(%d) with opcode 0x%x has multiple targets: %d and %d!",
							fromRoutineHash, fromIndex, branchNode.opcode, toIndex, branchNode.getTargetIndex());
				}
				if (userLevel < branchNode.getBranchUserLevel()) {
					reportBranchPermissionChange(fromRoutineHash, branchNode, userLevel);
					if (skipRequestCount > 0 || mergeRequestCount > 0)
						branchNode.setBranchUserLevel(userLevel);
				}
			}
		} else {
			toGraph = getRoutineGraph(fromRoutineHash);
			if (toGraph == null) {
				throw new MergeException("Request edge 0x%x(%d) -%d-> 0x%x(%d) to unknown routine", fromRoutineHash,
						fromIndex, toRoutineHash, toIndex);
			}

			GraphEdgeSet.AddEdgeResult addResult = mergedEdges.addCallEdge(fromRoutineHash, fromNode, toRoutineHash,
					userLevel, !(skipRequestCount > 0 || mergeRequestCount > 0));
			reportEdgeAddResult(addResult, fromNode);
		}
		return true;
	}

	@Override
	public void addRoutine(ScriptRoutineGraph routine) {
		throw new MergeException("Cannot add routines to a %s", getClass().getSimpleName());
	}

	private ScriptRoutineGraph getRoutineGraph(int hash) {
		if (ScriptRoutineGraph.isDynamicRoutine(hash)) {
			Log.warn("Warning: %s does not support dynamic routines yet! Skipping routine.", getClass().getSimpleName());
			return null;
		}

		ScriptRoutineGraph graph = mergedStaticRoutines.get(hash);
		if (graph == null) {
			rightGraph.getRoutine(hash);
			if (graph == null)
				graph = leftGraph.getRoutine(hash);
			if (graph != null)
				mergedStaticRoutines.put(hash, graph);
		}
		return graph;
	}

	private String printMode() {
		if (skipRequestCount > 0)
			return String.format("skipping @ %d # %d", skipRequestCount, currentRequestId);
		else if (mergeRequestCount > 0)
			return String.format("merging @ %d # %d", mergeRequestCount, currentRequestId);
		else
			return String.format("evaluating @ %d # %d", evaluationCount, currentRequestId);
	}

	private void reportEdgeAddResult(GraphEdgeSet.AddEdgeResult addResult, ScriptNode fromNode) {
		if (addResult == null)
			return;

		switch (addResult.type) {
			case LOWER_USER_LEVEL:
				LowerUserLevelResult chmod = (LowerUserLevelResult) addResult;
				if (chmod.toUserLevel < 2) {
					Log.log("[%s] Lower user level from %s to %s on edge %s (#%d) -> %s", printMode(),
							RoutineEdge.printUserLevel(chmod.fromUserLevel),
							RoutineEdge.printUserLevel(chmod.toUserLevel), chmod.edge.printFromNode(),
							fromNode.lineNumber, chmod.edge.printToNode());
				}
				break;
			case NEW_EDGE:
				NewEdgeResult newEdge = (NewEdgeResult) addResult;
				if (newEdge.edge.getUserLevel() < 2) {
					Log.log("[%s] New edge %s (#%d) -> %s with user level %s", printMode(),
							newEdge.edge.printFromNode(), fromNode.lineNumber, newEdge.edge.printToNode(),
							RoutineEdge.printUserLevel(newEdge.edge.getUserLevel()));
					for (RoutineEdge existingEdge : mergedEdges.getOutgoingEdges(fromNode)) {
						if (existingEdge.getToRoutineHash() != newEdge.edge.getToRoutineHash()) {
							Log.log("\tExisting edge %s (#%d) -> %s with user level %s", existingEdge.printFromNode(),
									fromNode.lineNumber, existingEdge.printToNode(),
									RoutineEdge.printUserLevel(existingEdge.getUserLevel()));
						}
					}
				}
				break;
		}
	}

	private void reportBranchPermissionChange(int routineHash, ScriptBranchNode branchNode, int userLevel) {
		Log.log("[%s] Lower user level from %s to %s on edge 0x%x(%d (#%d) -> %d)", printMode(),
				RoutineEdge.printUserLevel(branchNode.getBranchUserLevel()), RoutineEdge.printUserLevel(userLevel),
				routineHash, branchNode.index, branchNode.lineNumber, branchNode.getTargetIndex());
	}
}
