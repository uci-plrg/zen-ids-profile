package edu.uci.eecs.scriptsafe.analysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.analysis.RequestGraph.CallSite;
import edu.uci.eecs.scriptsafe.analysis.RequestGraph.Edge;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetFiles;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles;

public class GraphExporter {

	private enum Color {
		ADMIN("red"),
		ANONYMOUS("blue");

		final String name;

		private Color(String name) {
			this.name = name;
		}

		static Color forUserLevel(int userLevel) {
			if (userLevel < 2)
				return ANONYMOUS;
			else
				return ADMIN;
		}
	}

	public static final OptionArgumentMap.StringOption sourceGraphDir = OptionArgumentMap.createStringOption('s');
	public static final OptionArgumentMap.StringOption outputFilePath = OptionArgumentMap.createStringOption('o');
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

	private ScriptFlowGraph sourceGraph;
	private File outputFile;

	private GraphExporter(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, sourceGraphDir, outputFilePath, verbose, watchlistFile,
				watchlistCategories);
	}

	private void run() {
		try {
			ScriptNode.init();

			argMap.parseOptions();

			Log.addOutput(System.out);
			Log.setLevel(Log.Level.values()[verbose.getValue()]);
			System.out.println("Log level " + verbose.getValue());

			if (!(sourceGraphDir.hasValue() && outputFilePath.hasValue())) {
				printUsage();
				return;
			}

			File sourcePath = new File(sourceGraphDir.getValue());
			requestLoader.addPath(sourcePath.toPath());
			requestGraph = requestLoader.load();

			if (watchlistFile.hasValue()) {
				File watchlist = new File(watchlistFile.getValue());
				ScriptMergeWatchList.getInstance().loadFromFile(watchlist);
			}
			if (watchlistCategories.hasValue()) {
				ScriptMergeWatchList.getInstance().activateCategories(watchlistCategories.getValue());
			}

			outputFile = new File(outputFilePath.getValue());

			generateGraph();

		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void generateGraph() throws FileNotFoundException {
		PrintWriter out = new PrintWriter(outputFile);
		try {
			out.println("digraph g {");
			out.println("  node [shape=point]");

			for (CallSite callSite : requestGraph.callSites.values()) {
				for (Edge edge : callSite.edges) {
					out.format("  \"0x%x:%d\" -> \"0x%x\" [color=%s weight=%d]\n", callSite.routine.hash,
							callSite.node.lineNumber, edge.callee.hash, Color.forUserLevel(edge.userLevel).name,
							edge.count);
				}
			}
			out.println("}");
		} finally {
			out.flush();
			out.close();
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -s <run-dir> -o <output-file>", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		GraphExporter exporter = new GraphExporter(stack);
		exporter.run();
	}
}
