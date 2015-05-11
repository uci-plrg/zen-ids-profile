package edu.uci.eecs.scriptsafe.analysis.request;

import edu.uci.eecs.scriptsafe.merge.graph.RoutineId;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class RequestEdgeSummary {
	public final RequestCallSiteSummary callSite;
	public final RoutineId calleeId;
	public final ScriptRoutineGraph callee;

	int adminCount = 0;
	int anonymousCount = 0;

	RequestEdgeSummary(RequestCallSiteSummary callSite, RoutineId calleeId, ScriptRoutineGraph callee) {
		this.callSite = callSite;
		this.calleeId = calleeId;
		this.callee = callee;
	}

	public int getAdminCount() {
		return adminCount;
	}

	public int getAnonymousCount() {
		return anonymousCount;
	}

	boolean matches(ScriptRoutineGraph callee) {
		return this.callee.hash == callee.hash;
	}
}
