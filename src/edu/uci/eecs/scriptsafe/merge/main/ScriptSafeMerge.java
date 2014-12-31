package edu.uci.eecs.scriptsafe.merge.main;

import java.io.File;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.scriptsafe.merge.ScriptDatasetGenerator;
import edu.uci.eecs.scriptsafe.merge.ScriptMerge;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataSource;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptRunFileSet;

public class ScriptSafeMerge {

	public static final OptionArgumentMap.StringOption leftGraphDir = OptionArgumentMap.createStringOption('l');
	public static final OptionArgumentMap.StringOption rightGraphDir = OptionArgumentMap.createStringOption('r');
	public static final OptionArgumentMap.StringOption outputDir = OptionArgumentMap.createStringOption('o');

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private final ScriptGraphLoader loader = new ScriptGraphLoader();

	private ScriptGraphDataSource leftDataSource;
	private ScriptGraphDataSource rightDataSource;
	private ScriptFlowGraph leftGraph;
	private ScriptFlowGraph rightGraph;

	private ScriptSafeMerge(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, leftGraphDir, rightGraphDir, outputDir);
	}

	private void run() {
		try {
			Log.addOutput(System.out);

			argMap.parseOptions();

			if (!leftGraphDir.hasValue() || !rightGraphDir.hasValue() || !outputDir.hasValue()) {
				printUsage();
				return;
			}

			File leftPath = new File(leftGraphDir.getValue());
			File rightPath = new File(rightGraphDir.getValue());
			leftDataSource = ScriptGraphDataSource.Factory.construct(leftPath);
			rightDataSource = ScriptGraphDataSource.Factory.construct(rightPath);

			leftGraph = loader.loadGraph(leftDataSource);
			rightGraph = loader.loadGraph(rightDataSource);

			Log.log("Left graph is a %s from %s with %d routines", leftDataSource.getClass().getSimpleName(),
					leftPath.getAbsolutePath(), leftGraph.getRoutineCount());
			Log.log("Right graph is a %s from %s with %d routines", rightDataSource.getClass().getSimpleName(),
					rightPath.getAbsolutePath(), rightGraph.getRoutineCount());

			ScriptMerge merge = new ScriptMerge(leftGraph, rightGraph);
			ScriptFlowGraph merged = merge.merge();

			Log.log("Merged graph is a %s with %d routines and %d eval routines", merged.getClass().getSimpleName(),
					merged.getRoutineCount(), merged.getEvalProxyCount());

			File outputFile = new File(outputDir.getValue());
			ScriptDatasetGenerator output = new ScriptDatasetGenerator(merged);
			output.generateDataset(outputFile);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -l <left-data-source> -r <right-data-source> -o <output-dir>",
				getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		ScriptSafeMerge merge = new ScriptSafeMerge(stack);
		merge.run();
	}
}
