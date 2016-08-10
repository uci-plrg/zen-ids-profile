package edu.uci.plrg.cfi.php.analysis.request;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.plrg.cfi.php.analysis.request.RequestCallSiteSummary.CallSiteKey;
import edu.uci.plrg.cfi.php.merge.graph.RoutineId;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;
import edu.uci.plrg.cfi.php.merge.graph.loader.ScriptNodeLoader;

public class RequestGraph implements RequestSequenceLoader.RequestCollection {

	public final Map<Integer, ScriptRoutineGraph> routines = new HashMap<Integer, ScriptRoutineGraph>();
	public final Map<Integer, Integer> calledRoutineUserLevel = new HashMap<Integer, Integer>();
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

	public RequestEdgeSummary getEdge(int fromRoutineHash, int fromIndex, int toRoutineHash) {
		RequestCallSiteSummary callSite = getCallSite(fromRoutineHash, fromIndex);
		if (callSite == null)
			return null;
		else
			return callSite.getEdge(toRoutineHash);
	}

	public boolean addEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, int toIndex, int userLevel,
			File routineCatalog) throws NumberFormatException, IOException {
		RequestCallSiteSummary callSite = establishCallSite(
				RoutineId.Cache.INSTANCE.getId(routineCatalog, fromRoutineHash), fromRoutineHash, fromIndex);
		callSite.addEdge(RoutineId.Cache.INSTANCE.getId(routineCatalog, toRoutineHash), routines.get(toRoutineHash),
				userLevel);

		Integer currentUserLevel = calledRoutineUserLevel.get(toRoutineHash);
		if (currentUserLevel == null || currentUserLevel > userLevel)
			calledRoutineUserLevel.put(toRoutineHash, userLevel);

		return true;
	}

	public void addRoutine(ScriptRoutineGraph routine) {
		routines.put(routine.hash, routine);
	}

	public boolean startRequest(int requestId, File routineCatalog) {
		return true;
	}

	@Override
	public ScriptRoutineGraph createRoutine(int routineHash) {
		ScriptRoutineGraph routine = new ScriptRoutineGraph(routineHash, RoutineId.Cache.INSTANCE.getId(routineHash),
				false);
		routines.put(routine.hash, routine);
		return routine;
	}

	@Override
	public ScriptRoutineGraph getRoutine(int routineHash) {
		return routines.get(routineHash);
	}

	private RequestCallSiteSummary establishCallSite(RoutineId routineId, int routineHash, int nodeIndex) {
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
