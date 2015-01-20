package edu.uci.eecs.scriptsafe.merge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.scriptsafe.merge.graph.GraphEdgeSet;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge.Type;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineExceptionEdge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptMerge implements ScriptDatasetGenerator.DataSource {

	public enum Side {
		LEFT,
		RIGHT;
	}

	final ScriptFlowGraph left;
	final ScriptFlowGraph right;

	private final Map<Long, ScriptRoutineGraph> mergedStaticRoutines = new HashMap<Long, ScriptRoutineGraph>();
	private final GraphEdgeSet mergedEdges = new GraphEdgeSet();
	private final DynamicRoutineMerge dynamicRoutineMerge;

	public ScriptMerge(ScriptFlowGraph left, ScriptFlowGraph right, boolean isIncremental) {
		this.left = left;
		this.right = right;

		if (isIncremental)
			dynamicRoutineMerge = new IncrementalDynamicRoutineMerge(left);
		else
			dynamicRoutineMerge = new BaseDynamicRoutineMerge(left, right);
	}

	public void merge() {
		for (ScriptRoutineGraph rightRoutine : right.getRoutines()) {
			if (ScriptRoutineGraph.isDynamicRoutine(rightRoutine.id))
				dynamicRoutineMerge.addDynamicRoutine(rightRoutine, Side.RIGHT);
			else
				mergedStaticRoutines.put(rightRoutine.id, rightRoutine);
		}
		for (ScriptRoutineGraph leftRoutine : left.getRoutines()) {
			if (ScriptRoutineGraph.isDynamicRoutine(leftRoutine.id)) {
				dynamicRoutineMerge.addDynamicRoutine(leftRoutine, Side.LEFT);
			} else {
				ScriptRoutineGraph rightRoutine = mergedStaticRoutines.get(leftRoutine.id);
				if (rightRoutine == null)
					mergedStaticRoutines.put(leftRoutine.id, leftRoutine);
				else
					leftRoutine.verifySameRoutine(rightRoutine);
			}
		}

		addRoutineEdges(left, Side.LEFT);
		addRoutineEdges(right, Side.RIGHT);

		// dataset generator writes from here (interface so it can also write plain graphs?)
		// nix cloner (hopefully)
	}

	private void addRoutineEdges(ScriptFlowGraph graph, Side fromSide) {
		for (List<RoutineEdge> edges : graph.edges.getOutgoingEdges()) {
			for (RoutineEdge edge : edges) {
				if (edge.getEntryType() == Type.THROW) {
					RoutineExceptionEdge throwEdge = (RoutineExceptionEdge) edge;
					mergedEdges.addExceptionEdge(resolveRoutineId(throwEdge.getFromRoutineId(), fromSide),
							getNode(throwEdge.getFromRoutineId(), fromSide, throwEdge.getFromRoutineIndex()),
							resolveRoutineId(throwEdge.getToRoutineId(), fromSide), throwEdge.getToRoutineIndex());
				} else {
					mergedEdges.addCallEdge(resolveRoutineId(edge.getFromRoutineId(), fromSide),
							getNode(edge.getFromRoutineId(), fromSide, edge.getFromRoutineIndex()),
							resolveRoutineId(edge.getToRoutineId(), fromSide));
				}
			}
		}
	}

	private long resolveRoutineId(long routineId, Side fromSide) {
		if (ScriptRoutineGraph.isDynamicRoutine(routineId)) {
			if (fromSide == Side.LEFT)
				return dynamicRoutineMerge
						.getNewLeftDynamicRoutineId(ScriptRoutineGraph.getDynamicRoutineId(routineId));
			else
				return dynamicRoutineMerge.getNewRightDynamicRoutineId(ScriptRoutineGraph
						.getDynamicRoutineId(routineId));
		} else {
			return routineId;
		}
	}

	private ScriptRoutineGraph getRoutineGraph(long routineId, Side fromSide) {
		if (ScriptRoutineGraph.isDynamicRoutine(routineId))
			return dynamicRoutineMerge.getMergedGraph(ScriptRoutineGraph.getDynamicRoutineId(routineId), fromSide);
		else
			return mergedStaticRoutines.get(routineId);
	}

	private ScriptNode getNode(long routineId, Side side, int index) {
		return getRoutineGraph(routineId, side).getNode(index);
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
