package edu.uci.eecs.scriptsafe.merge.main;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphFile;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphLoader;

public class ScriptSafeMerge {

	public static final OptionArgumentMap.StringOption crowdSafeCommonDir = OptionArgumentMap.createStringOption('d');

	private final ArgumentStack args;

	private final ScriptGraphLoader loader;

	private ScriptGraphFile leftFile;
	private ScriptGraphFile rightFile;
	private ScriptFlowGraph leftGraph;
	private ScriptFlowGraph rightGraph;

	private ScriptSafeMerge(ArgumentStack args) {
		this.args = args;
		loader = new ScriptGraphLoader();
	}

	private void run() {
		try {
			Log.addOutput(System.out);

			leftFile = ScriptGraphFile.fromPath(args.pop());
			rightFile = ScriptGraphFile.fromPath(args.pop());

			leftGraph = loader.loadGraph(leftFile);
			rightGraph = loader.loadGraph(rightFile);

			Log.log("Left graph is a %s from file %s", leftFile.type.name, leftFile.file.getAbsolutePath());
			Log.log("Right graph is a %s from file %s", rightFile.type.name, rightFile.file.getAbsolutePath());
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
