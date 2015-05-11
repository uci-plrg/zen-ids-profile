package edu.uci.eecs.scriptsafe.analysis.request;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.scriptsafe.analysis.request.RequestCallSiteSummary.CallSiteKey;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineId;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class RequestGraph {

	public final Map<Integer, ScriptRoutineGraph> routines = new HashMap<Integer, ScriptRoutineGraph>();
	public final Map<CallSiteKey, RequestCallSiteSummary> callSites = new HashMap<CallSiteKey, RequestCallSiteSummary>();
	public final Map<Integer, List<RequestCallSiteSummary>> callSitesByRoutine = new HashMap<Integer, List<RequestCallSiteSummary>>();
	public final Map<Path, List<ScriptRoutineGraph>> routinesBySourceFile = new HashMap<Path, List<ScriptRoutineGraph>>();

	private int totalRequests;

	public int getTotalRequests() {
		return totalRequests;
	}

	public void setTotalRequests(int totalRequests) {
		this.totalRequests = totalRequests;
	}

	public RequestCallSiteSummary getCallSite(int routineHash, int opcode) {
		CallSiteKey key = new CallSiteKey(routineHash, opcode);
		return callSites.get(key);
	}
	
	public RequestEdgeSummary getEdge(int fromRoutineHash, int fromOpcode, int toRoutineHash) {
		RequestCallSiteSummary callSite = getCallSite(fromRoutineHash, fromOpcode);
		return callSite.getEdge(toRoutineHash);
	}

	RequestCallSiteSummary establishCallSite(RoutineId routineId, int routineHash, int nodeIndex) {
		CallSiteKey key = new CallSiteKey(routineHash, nodeIndex);
		RequestCallSiteSummary site = callSites.get(key);
		if (site == null) {
			ScriptRoutineGraph routine = routines.get(routineHash);
			site = new RequestCallSiteSummary(routineId, routine, routine.getNode(nodeIndex));
			callSites.put(key, site);
			establishRoutineCallSites(routineHash).add(site);
			establishFileRoutines(site.id.sourceFile);
		}
		return site;
	}

	private List<RequestCallSiteSummary> establishRoutineCallSites(int routineHash) {
		List<RequestCallSiteSummary> sites = callSitesByRoutine.get(routineHash);
		if (sites == null) {
			sites = new ArrayList<RequestCallSiteSummary>();
			callSitesByRoutine.put(routineHash, sites);
		}
		return sites;
	}

	private List<ScriptRoutineGraph> establishFileRoutines(Path sourceFile) {
		List<ScriptRoutineGraph> routines = routinesBySourceFile.get(sourceFile);
		if (routines == null) {
			routines = new ArrayList<ScriptRoutineGraph>();
			routinesBySourceFile.put(sourceFile, routines);
		}
		return routines;
	}
}