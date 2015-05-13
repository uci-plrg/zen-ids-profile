package edu.uci.eecs.scriptsafe.analysis.request;

import edu.uci.eecs.scriptsafe.feature.FeatureRoleCountElement;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineId;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class RequestEdgeSummary implements FeatureRoleCountElement {
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

	@Override
	public int getAdminCount() {
		return adminCount > 0 ? 1 : 0;
	}

	@Override
	public int getAnonymousCount() {
		return anonymousCount > 0 ? 1 : 0;
	}

	boolean matches(ScriptRoutineGraph callee) {
		return this.callee.hash == callee.hash;
	}
}
