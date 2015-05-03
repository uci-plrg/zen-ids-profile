package edu.uci.eecs.scriptsafe.merge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.GraphEdgeSet;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge.Type;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineExceptionEdge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles;

public class DatasetMerge implements ScriptDatasetGenerator.DataSource {

	public enum Side {
		LEFT,
		RIGHT;
	}

	final ScriptFlowGraph left;
	final ScriptFlowGraph right;

	private final Map<Integer, ScriptRoutineGraph> mergedStaticRoutines = new HashMap<Integer, ScriptRoutineGraph>();
	private final GraphEdgeSet mergedEdges = new GraphEdgeSet();
	private final DynamicRoutineMerge dynamicRoutineMerge;

	private final Map<Integer, Integer> userLevelDeltas = new HashMap<Integer, Integer>(); // routineHash->userLevel
	private int maxUserLevelDelta = 0;

	public DatasetMerge(ScriptFlowGraph left, ScriptFlowGraph right, boolean isIncremental) {
		this.left = left;
		this.right = right;

		if (isIncremental)
			dynamicRoutineMerge = new IncrementalDynamicRoutineMerge(left);
		else
			dynamicRoutineMerge = new BaseDynamicRoutineMerge(left, right);
	}

	public void merge() {
		Log.log("Add right routines");
		for (ScriptRoutineGraph rightRoutine : right.getRoutines()) {
			if (ScriptRoutineGraph.isDynamicRoutine(rightRoutine.hash))
				dynamicRoutineMerge.addDynamicRoutine(rightRoutine, Side.RIGHT);
			else
				mergedStaticRoutines.put(rightRoutine.hash, rightRoutine);
		}
		Log.log("Add left routines");
		for (ScriptRoutineGraph leftRoutine : left.getRoutines()) {
			if (ScriptRoutineGraph.isDynamicRoutine(leftRoutine.hash)) {
				dynamicRoutineMerge.addDynamicRoutine(leftRoutine, Side.LEFT);
			} else {
				ScriptRoutineGraph rightRoutine = mergedStaticRoutines.get(leftRoutine.hash);

				if (rightRoutine == null
						&& ScriptMergeWatchList.getInstance().isActive(ScriptMergeWatchList.Category.ROUTINE))
					Log.spot("Adding new routine 0x%x", leftRoutine.hash);

				if (rightRoutine == null) {
					mergedStaticRoutines.put(leftRoutine.hash, leftRoutine);
				} else {
					try {
						rightRoutine.mergeRoutine(leftRoutine);
					} catch (MergeException e) {
						Log.error("Incompatible routine graphs for 0x%x: %s", leftRoutine.hash, e.getMessage());
					}
				}
			}
		}

		addRoutineEdges(right, Side.RIGHT);
		addRoutineEdges(left, Side.LEFT);
		reportUserLevelDeltas();

		// dataset generator writes from here (interface so it can also write plain graphs?)
		// nix cloner (hopefully)
	}

	private void addRoutineEdges(ScriptFlowGraph graph, Side fromSide) {
		boolean added;
		int toRoutineHash, minUserLevel;

		for (List<RoutineEdge> edges : graph.edges.getOutgoingEdges()) {
			for (RoutineEdge edge : edges) {
				try {
					toRoutineHash = resolveRoutineIndex(edge.getToRoutineHash(), fromSide);
					minUserLevel = mergedEdges.getMinUserLevel(toRoutineHash);
					if (edge.getUserLevel() > maxUserLevelDelta)
						maxUserLevelDelta = edge.getUserLevel();

					if (edge.getEntryType() == Type.THROW) {
						RoutineExceptionEdge throwEdge = (RoutineExceptionEdge) edge;
						added = mergedEdges.addExceptionEdge(
								resolveRoutineIndex(throwEdge.getFromRoutineHash(), fromSide),
								getNode(throwEdge.getFromRoutineHash(), fromSide, throwEdge.getFromRoutineIndex()),
								resolveRoutineIndex(throwEdge.getToRoutineHash(), fromSide),
								throwEdge.getToRoutineIndex(), throwEdge.getUserLevel());

						if (ScriptMergeWatchList.watchAny(throwEdge.getFromRoutineHash(),
								throwEdge.getFromRoutineIndex())
								|| ScriptMergeWatchList.watch(throwEdge.getToRoutineHash())
								|| (added && graph.dataSourceType == ScriptGraphDataFiles.Type.RUN && ScriptMergeWatchList
										.getInstance().isActive(ScriptMergeWatchList.Category.EXCEPTION_EDGE))) {
							Log.log("Merged exception edge from the %s: %s (0x%x) -%s-> %s",
									fromSide,
									throwEdge.printFromNode(),
									getNode(throwEdge.getFromRoutineHash(), fromSide, throwEdge.getFromRoutineIndex()).opcode,
									throwEdge.printUserLevel(), throwEdge.printToNode());
						}
					} else {
						added = mergedEdges.addCallEdge(resolveRoutineIndex(edge.getFromRoutineHash(), fromSide),
								getNode(edge.getFromRoutineHash(), fromSide, edge.getFromRoutineIndex()),
								toRoutineHash, edge.getUserLevel());

						if (ScriptMergeWatchList.watchAny(edge.getFromRoutineHash(), edge.getFromRoutineIndex())
								|| ScriptMergeWatchList.watch(edge.getToRoutineHash())
								|| (added && graph.dataSourceType == ScriptGraphDataFiles.Type.RUN && ScriptMergeWatchList
										.getInstance().isActive(ScriptMergeWatchList.Category.ROUTINE_EDGE))) {
							Log.log("Merged call edge from the %s: %s (0x%x) -%s-> %s", fromSide, edge.printFromNode(),
									getNode(edge.getFromRoutineHash(), fromSide, edge.getFromRoutineIndex()).opcode,
									edge.printUserLevel(), edge.printToNode());
						}
					}
					if (added && graph.dataSourceType == ScriptGraphDataFiles.Type.RUN
							&& minUserLevel > edge.getUserLevel()) {
						if (ScriptMergeWatchList.getInstance().isActive(ScriptMergeWatchList.Category.FLOW_USER_LEVEL))
							userLevelDeltas.put(edge.getToRoutineHash(), edge.getUserLevel());
						if (ScriptMergeWatchList.getInstance().isActive(
								ScriptMergeWatchList.Category.ROUTINE_USER_LEVEL)) {
							Log.log("<UL> %s -> %d 0x%x", RoutineEdge.printUserLevel(minUserLevel),
									edge.getUserLevel(), edge.getToRoutineHash());
						}
					}
				} catch (Throwable t) {
					Log.error("Failed to add routine edge from the %S side: 0x%x[0x%x]:%d -%s-> 0x%x:%d (%s: %s)",
							fromSide, edge.getFromRoutineHash(),
							resolveRoutineIndex(edge.getFromRoutineHash(), fromSide), edge.getFromRoutineIndex(), edge
									.printUserLevel(), edge.getToRoutineHash(), edge.getEntryType() == Type.CALL ? 0
									: ((RoutineExceptionEdge) edge).getToRoutineIndex(), t.getClass().getSimpleName(),
							t.getMessage());
				}
			}
		}
	}

	private void reportUserLevelDeltas() {
		if (!ScriptMergeWatchList.getInstance().isActive(ScriptMergeWatchList.Category.FLOW_USER_LEVEL))
			return;

		if (userLevelDeltas.isEmpty())
			Log.log("No user level changes");

		int deltaCounts[] = new int[maxUserLevelDelta + 1];
		for (Integer userLevel : userLevelDeltas.values())
			deltaCounts[userLevel]++;
		for (int userLevel = 0; userLevel < deltaCounts.length; userLevel++) {
			if (deltaCounts[userLevel] > 0)
				Log.log("Lowered user level of %d routines to %d", deltaCounts[userLevel], userLevel);
		}
	}

	private int resolveRoutineIndex(int routineIndex, Side fromSide) {
		if (ScriptRoutineGraph.isDynamicRoutine(routineIndex)) {
			if (fromSide == Side.LEFT)
				return ScriptRoutineGraph.constructDynamicHash(dynamicRoutineMerge
						.getNewLeftDynamicRoutineIndex(ScriptRoutineGraph.getDynamicRoutineIndex(routineIndex)));
			else
				return ScriptRoutineGraph.constructDynamicHash(dynamicRoutineMerge
						.getNewRightDynamicRoutineIndex(ScriptRoutineGraph.getDynamicRoutineIndex(routineIndex)));
		} else {
			return routineIndex;
		}
	}

	private ScriptRoutineGraph getRoutineGraph(int routineHash, Side fromSide) {
		if (ScriptRoutineGraph.isDynamicRoutine(routineHash))
			return dynamicRoutineMerge.getMergedGraph(ScriptRoutineGraph.getDynamicRoutineIndex(routineHash), fromSide);
		else
			return mergedStaticRoutines.get(routineHash);
	}

	private ScriptNode getNode(int routineHash, Side side, int index) {
		return getRoutineGraph(routineHash, side).getNode(index);
	}

	public int getRoutineCount() {
		return mergedStaticRoutines.size() + dynamicRoutineMerge.mergedGraphs.size();
	}

	public int getDynamicRoutineCount() {
		return dynamicRoutineMerge.mergedGraphs.size();
	}

	@Override
	public Iterable<ScriptRoutineGraph> getDynamicRoutines() {
		return dynamicRoutineMerge.mergedGraphs;
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
	public int getStaticRoutineCount() {
		return mergedStaticRoutines.size();
	}

	@Override
	public Iterable<ScriptRoutineGraph> getStaticRoutines() {
		return mergedStaticRoutines.values();
	}
}
