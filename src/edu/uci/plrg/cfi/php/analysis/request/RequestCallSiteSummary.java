package edu.uci.plrg.cfi.php.analysis.request;

import java.util.ArrayList;
import java.util.List;

import edu.uci.plrg.cfi.php.merge.graph.RoutineId;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;

public class RequestCallSiteSummary {

	public static class CallSiteKey {
		final int routineHash;
		final int nodeIndex;

		public CallSiteKey(int routineHash, int nodeIndex) {
			this.routineHash = routineHash;
			this.nodeIndex = nodeIndex;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + nodeIndex;
			result = prime * result + routineHash;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			CallSiteKey other = (CallSiteKey) obj;
			if (nodeIndex != other.nodeIndex)
				return false;
			if (routineHash != other.routineHash)
				return false;
			return true;
		}
	}

	public final RoutineId id;
	public final ScriptRoutineGraph routine;
	public final ScriptNode node;
	
	private final List<RequestEdgeSummary> edges = new ArrayList<RequestEdgeSummary>();

	double regularity = 0.0;

	RequestCallSiteSummary(RoutineId id, ScriptRoutineGraph routine, ScriptNode node) {
		this.id = id;
		this.routine = routine;
		this.node = node;
	}

	public RequestEdgeSummary getEdge(int targetRoutineHash) {
		for (RequestEdgeSummary edge : edges) {
			if (edge.callee.hash == targetRoutineHash)
				return edge;
		}
		return null;
	}
	
	public Iterable<RequestEdgeSummary> getEdges() {
		return edges;
	}

	public double getRegularity() {
		return regularity;
	}

	void addEdge(RoutineId calleeId, ScriptRoutineGraph callee, int userLevel) {
		RequestEdgeSummary edge = null;
		for (RequestEdgeSummary e : edges) {
			if (e.matches(callee)) {
				edge = e;
				break;
			}
		}
		if (edge == null) {
			edge = new RequestEdgeSummary(this, calleeId, callee);
			edges.add(edge);
		}
		if (userLevel < 2)
			edge.anonymousCount++;
		else
			edge.adminCount++;
	}

	public void calculateRegularity() {
		if (edges.size() == 1) {
			regularity = 1.0;
			return;
		}

		int totalCalls = 0, maxCalls = 0, minCalls = Integer.MAX_VALUE, edgeCalls = 0;
		for (RequestEdgeSummary edge : edges) {
			edgeCalls = (edge.adminCount + edge.anonymousCount);
			totalCalls += edgeCalls;
			if (edgeCalls > maxCalls)
				maxCalls = edgeCalls;
			if (edgeCalls < minCalls)
				minCalls = edgeCalls;
		}
		regularity = (minCalls / (double) maxCalls);
		if (regularity < 0.5) {
			double normalizer = 1 + (.5 / (double) Math.min(1, totalCalls / 10));
			regularity *= normalizer;
		}
	}
}
