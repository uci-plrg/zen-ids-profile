package edu.uci.eecs.scriptsafe.analysis;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.analysis.RequestGraph.CallSite;
import edu.uci.eecs.scriptsafe.analysis.RequestGraph.Edge;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;

public class EdgeRegularityAnalysis {

	private static class RegularitySorter implements Comparator<CallSite> {
		@Override
		public int compare(CallSite first, CallSite second) {
			return ((int) (100000 * first.regularity)) - ((int) (100000 * second.regularity));
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

	private final RequestGraph.Loader requestLoader = new RequestGraph.Loader();
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

			requestGraph = requestLoader.load();

			List<CallSite> callSitesByRegularity = new ArrayList<CallSite>();
			for (CallSite callSite : requestGraph.callSites.values()) {
				callSite.calculateRegularity();
				callSitesByRegularity.add(callSite);
			}
			Collections.sort(callSitesByRegularity, new RegularitySorter());
			for (CallSite callSite : callSitesByRegularity) {
				if (callSite.regularity > 0.5)
					break;
				Log.log("%02.03f%% %s (0x%x):%d", callSite.regularity * 100, callSite.name, callSite.routine.hash,
						callSite.node.lineNumber);
				for (Edge edge : callSite.edges)
					Log.log("\t%04d: -%02d-> %s (0x%x)", edge.count, edge.userLevel, edge.calleeName, edge.callee.hash);
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
