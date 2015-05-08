package edu.uci.eecs.scriptsafe.analysis;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap.OptionMode;
import edu.uci.eecs.scriptsafe.analysis.RequestGraph.CallSite;
import edu.uci.eecs.scriptsafe.analysis.RequestGraph.Edge;
import edu.uci.eecs.scriptsafe.merge.ScriptMergeWatchList;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;

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

	private static class TabbedFormatter {
		private static final int INDENTATION = 2;

		private final PrintWriter out;

		private char indent[] = new char[0];

		TabbedFormatter(PrintWriter out) {
			this.out = out;
		}

		void indent(int count) {
			indent = new char[indent.length + (count * INDENTATION)];
			Arrays.fill(indent, ' ');
		}

		void unindent(int count) {
			indent = new char[Math.max(0, indent.length - (count * INDENTATION))];
			Arrays.fill(indent, ' ');
		}

		void println(String format, Object... args) {
			out.print(indent);
			out.format(format, args);
			out.println();
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
		TabbedFormatter out = new TabbedFormatter(new PrintWriter(outputFile));
		try {
			out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
			out.println("<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\"");
			out.indent(2);
			out.println("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"");
			out.println("xsi:schemaLocation=\"http://graphml.graphdrawing.org/xmlns");
			out.println("http://graphml.graphdrawing.org/xmlns/1.0/graphml.xsd\">");
			out.unindent(1);
			out.println("<graph id=\"G\" edgedefault=\"directed\">");
			out.indent(1);

			for (Map.Entry<Integer, List<CallSite>> entry : requestGraph.callSitesByRoutine.entrySet()) {
				out.println("<node id=\"0x%x\"/>", entry.getKey());
				for (CallSite site : entry.getValue()) {
					out.println("<node id=\"0x%x:%d\"/>", site.routine.hash, site.node.index);
					out.println("<edge source=\"0x%x\" target=\"0x%x:%d\"/>", entry.getKey(), site.routine.hash,
							site.node.index);
				}
			}
			for (CallSite callSite : requestGraph.callSites.values()) {
				for (Edge edge : callSite.edges) {
					out.println(
							"<edge source=\"0x%x:%d\" target=\"0x%x\" admin-weight=\"%d\" anonymous-weight=\"%d\"/>",
							callSite.routine.hash, callSite.node.lineNumber, edge.callee.hash, edge.adminCount,
							edge.anonymousCount);
				}
			}
			out.unindent(1);
			out.println("</graph>");
			out.unindent(1);
			out.println("</graphml>");
		} finally {
			out.out.flush();
			out.out.close();
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
