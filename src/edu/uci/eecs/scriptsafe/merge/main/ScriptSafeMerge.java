package edu.uci.eecs.scriptsafe.merge.main;

import java.io.File;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataSource;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptRunFileSet;

public class ScriptSafeMerge {

	public static final OptionArgumentMap.StringOption crowdSafeCommonDir = OptionArgumentMap.createStringOption('d');

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final ScriptGraphLoader loader = new ScriptGraphLoader();

	private ScriptGraphDataSource leftDataSource;
	private ScriptGraphDataSource rightDataSource;
	private ScriptFlowGraph leftGraph;
	private ScriptFlowGraph rightGraph;

	private ScriptSafeMerge(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args);
	}

	private void run() {
		try {
			Log.addOutput(System.out);

			argMap.parseOptions();

			//args.popOptions();
			if (args.size() != 2) {
				printUsage();
				return;
			}

			File leftPath = new File(args.pop());
			File rightPath = new File(args.pop());
			leftDataSource = ScriptGraphDataSource.Factory.construct(leftPath);
			rightDataSource = ScriptGraphDataSource.Factory.construct(rightPath);

			leftGraph = loader.loadGraph(leftDataSource);
			rightGraph = loader.loadGraph(rightDataSource);

			Log.log("Left graph is a %s from %s with %d routines", leftDataSource.getClass().getSimpleName(),
					leftPath.getAbsolutePath(), leftGraph.getRoutineCount());
			Log.log("Right graph is a %s from %s with %d routines", rightDataSource.getClass().getSimpleName(),
					rightPath.getAbsolutePath(), rightGraph.getRoutineCount());
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void printUsage() {
		System.err.println(String
				.format("Usage: %s <left-data-source> <right-data-source>", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		ScriptSafeMerge merge = new ScriptSafeMerge(stack);
		merge.run();
	}
}
