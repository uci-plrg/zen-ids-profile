package edu.uci.eecs.scriptsafe.merge.main;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptRunFileSet;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphLoader;

public class ScriptSafeMerge {

	public static final OptionArgumentMap.StringOption crowdSafeCommonDir = OptionArgumentMap.createStringOption('d');

	private final ArgumentStack args;

	private final ScriptGraphLoader loader = new ScriptGraphLoader();

	private ScriptRunFileSet leftFiles;
	private ScriptRunFileSet rightFiles;
	private ScriptFlowGraph leftGraph;
	private ScriptFlowGraph rightGraph;

	private ScriptSafeMerge(ArgumentStack args) {
		this.args = args;
	}

	private void run() {
		try {
			Log.addOutput(System.out);

			leftFiles = new ScriptRunFileSet(args.pop());
			rightFiles = new ScriptRunFileSet(args.pop());

			leftGraph = loader.loadGraph(leftFiles);
			rightGraph = loader.loadGraph(rightFiles);

			Log.log("Left graph is a %s from file %s with %d routines", leftFiles.type.name,
					leftFiles.file.getAbsolutePath(), leftGraph.getRoutineCount());
			Log.log("Right graph is a %s from file %s with %d routines", rightFiles.type.name,
					rightFiles.file.getAbsolutePath(), rightGraph.getRoutineCount());
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		ScriptSafeMerge merge = new ScriptSafeMerge(stack);
		merge.run();
	}
}
