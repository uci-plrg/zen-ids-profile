package edu.uci.eecs.scriptsafe.feature;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;
import edu.uci.eecs.scriptsafe.analysis.request.RequestSequenceLoader;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptDataFilename;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;

public class CrossValidationSetGenerator {

	public static final OptionArgumentMap.IntegerOption kOption = OptionArgumentMap.createIntegerOption('k');
	public static final OptionArgumentMap.StringOption datasetDir = OptionArgumentMap.createStringOption('d');
	public static final OptionArgumentMap.StringOption outputFilePath = OptionArgumentMap.createStringOption('o');

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private CrossValidationSetGenerator(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, kOption, datasetDir, outputFilePath);
	}

	private void run() {
		ScriptNode.init();
		Log.addOutput(System.out);
		argMap.parseOptions();

		try {
			if (!(kOption.hasValue() && datasetDir.hasValue() && outputFilePath.hasValue())) {
				printUsage();
				return;
			}

			int k = kOption.getValue();
			File requestFile = ScriptDataFilename.REQUEST_GRAPH.requireFile(new File(datasetDir.getValue()));
			int requestCount = RequestSequenceLoader.peekRequestCount(requestFile);

			if (k > requestCount) {
				Log.error("The number of groups 'k' (%d) must not be greater than the total number of requests (%d)",
						k, requestCount);
				return;
			}

			List<Integer> requestIds = new ArrayList<Integer>();
			for (int i = 0; i < requestCount; i++)
				requestIds.add(i + 1);
			Collections.shuffle(requestIds);

			int groupSize = requestCount / k;
			int remainder = requestCount - (groupSize * k);

			File outputFile = new File(outputFilePath.getValue());

			Log.log("Writing %d request groups of size ~%d to file '%s'", k, groupSize, outputFilePath.getValue());

			PrintWriter out = new PrintWriter(new FileWriter(outputFile));
			try {
				int index = 0;
				for (int i = 0; i < k; i++) {
					int end = (i < remainder) ? groupSize + 1 : groupSize;
					for (int j = 0; j < end; j++)
						out.format("%d,", requestIds.get(index++));
					out.println();
				}
			} finally {
				out.flush();
				out.close();
			}
		} catch (Throwable t) {
			Log.error("Uncaught %s exception:", t.getClass().getSimpleName());
			Log.log(t);
		}
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -k <group-count> -d <dataset-dir> -o <output-file>", getClass()
				.getSimpleName()));
	}

	public static void main(String args[]) {
		try {
			ArgumentStack stack = new ArgumentStack(args);
			CrossValidationSetGenerator generator = new CrossValidationSetGenerator(stack);
			generator.run();
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
}
