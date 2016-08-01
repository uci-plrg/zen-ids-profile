package edu.uci.plrg.cfi.php.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.common.util.ArgumentStack;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap;
import edu.uci.plrg.cfi.common.util.OptionArgumentMap.OptionMode;
import edu.uci.plrg.cfi.php.analysis.request.RequestCallSiteSummary;
import edu.uci.plrg.cfi.php.analysis.request.RequestEdgeSummary;
import edu.uci.plrg.cfi.php.analysis.request.RequestGraph;
import edu.uci.plrg.cfi.php.analysis.request.RequestGraphLoader;
import edu.uci.plrg.cfi.php.merge.ScriptMergeWatchList;
import edu.uci.plrg.cfi.php.merge.graph.ScriptNode;

public class EdgeRegularityAnalysis {

	private static class RegularitySorter implements Comparator<RequestCallSiteSummary> {
		@Override
		public int compare(RequestCallSiteSummary first, RequestCallSiteSummary second) {
			return (int) Math.signum(first.getRegularity() - second.getRegularity());
		}
	}

	public static final OptionArgumentMap.IntegerOption verbose = OptionArgumentMap.createIntegerOption('v',
			Log.Level.ERROR.ordinal());
	public static final OptionArgumentMap.StringOption watchlistFile = OptionArgumentMap.createStringOption('w',
			OptionMode.OPTIONAL);
	public static final OptionArgumentMap.StringOption watchlistCategories = OptionArgumentMap.createStringOption('c',
			OptionMode.OPTIONAL);

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final RequestGraphLoader requestLoader = new RequestGraphLoader();
	private RequestGraph requestGraph;

	private EdgeRegularityAnalysis(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, verbose, watchlistFile, watchlistCategories);

	}

	private void run() {
		try {
			ScriptNode.init();

			argMap.parseOptions();

			Log.addOutput(System.out);
			Log.setLevel(Log.Level.values()[verbose.getValue()]);
			System.out.println("Log level " + verbose.getValue());

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}

			while (args.size() > 0) {
				File runDir = new File(args.pop());
				if (!(runDir.exists() && runDir.isDirectory()))
					throw new AnalysisException("Analysis target '%s' is not a directory.", runDir.getAbsolutePath());
				requestLoader.addPath(runDir.toPath());
			}

			Log.log("Analyzing uncommon edges in %d request-edge.run files.", requestLoader.getPathCount());

			requestGraph = new RequestGraph();
			requestLoader.load(requestGraph);

			List<RequestCallSiteSummary> callSitesByRegularity = new ArrayList<RequestCallSiteSummary>();
			for (RequestCallSiteSummary callSite : requestGraph.callSites.values()) {
				callSite.calculateRegularity();
				callSitesByRegularity.add(callSite);
			}
			Collections.sort(callSitesByRegularity, new RegularitySorter());
			for (RequestCallSiteSummary callSite : callSitesByRegularity) {
				if (callSite.getRegularity() > 0.5)
					break;
				Log.log("%02.03f%% %s (0x%x):%d", callSite.getRegularity() * 100, callSite.id, callSite.routine.hash,
						callSite.node.lineNumber);
				String majorityUserLevel;
				for (RequestEdgeSummary edge : callSite.getEdges()) {
					if (edge.getAdminCount() > edge.getAnonymousCount())
						majorityUserLevel = "ad";
					else if (edge.getAdminCount() < edge.getAnonymousCount())
						majorityUserLevel = "an";
					else
						majorityUserLevel = "eq";
					Log.log("\t%04d: -%s-> %s (0x%x)", (edge.getAdminCount() + edge.getAnonymousCount()),
							majorityUserLevel, edge.calleeId.id, edge.callee.hash);
				}
			}
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s <run-dir> [ <run-dir> ... ]", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		EdgeRegularityAnalysis analysis = new EdgeRegularityAnalysis(stack);
		analysis.run();
	}
}
