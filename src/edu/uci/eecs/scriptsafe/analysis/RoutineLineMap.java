package edu.uci.eecs.scriptsafe.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;

public class RoutineLineMap {

	private class CodeLine {
		final int lineNumber; // N.B.: based from 1
		final String code;

		CodeLine(int lineNumber, String code) {
			this.lineNumber = lineNumber;
			this.code = code;
		}
	}

	private class RoutineSpan {
		final int hash;
		final Path filePath;
		final List<CodeLine> code = new ArrayList<CodeLine>(); // N.B.: based from 0

		RoutineSpan(int hash, Path filePath) {
			this.hash = hash;
			this.filePath = filePath;
		}

		void addSpan(List<String> codeLines, int start, int end /* exclusive */) {
			for (int i = start; i < end; i++)
				code.add(new CodeLine(i, codeLines.get(i)));
		}

		void print(StringBuilder buffer) {
			if (code.isEmpty()) {
				buffer.append(String.format("Routine 0x%x in %s:empty\n", hash, filePath));
			} else {
				buffer.append(String.format("Routine 0x%x in %s:%d-%d\n", hash, filePath, code.get(0).lineNumber,
						code.get(code.size() - 1).lineNumber));
				for (CodeLine line : code)
					buffer.append(String.format("\t%04d: %s\n", line.lineNumber, line.code));
			}
		}
	}

	private class ApplicationFile {
		final File phpFile;
		final List<String> lines = new ArrayList<String>();
		final List<Integer> routines = new ArrayList<Integer>();

		ApplicationFile(File phpFile) throws IOException {
			this.phpFile = phpFile;

			BufferedReader in = new BufferedReader(new FileReader(phpFile));
			while (in.ready()) {
				lines.add(in.readLine());
			}
			in.close();
		}

		void installRoutineSpans(ScriptFlowGraph cfg) {
			if (lines.isEmpty())
				return;

			List<Integer> routineCoverage = new ArrayList<Integer>();
			for (int i = 0; i < lines.size(); i++)
				routineCoverage.add(0);
			for (Integer hash : routines) {
				ScriptRoutineGraph routine = cfg.getRoutine(hash);
				for (ScriptNode node : routine.getNodes()) {
					if (node.lineNumber >= routineCoverage.size()) {
						Log.error("Node has line number %d, but the file only has %d lines", node.lineNumber,
								routineCoverage.size());
					} else {
						routineCoverage.set(Math.max(0, node.lineNumber - 1), routine.hash);
					}
				}
			}

			int i = 0, hash, start, end = 0;
			RoutineSpan currentSpan = null, nextSpan;
			do {
				hash = routineCoverage.get(i++);
				if (i >= routineCoverage.size())
					return;
			} while (hash == 0);
			start = i;
			currentSpan = establishRoutineSpan(hash);

			for (; i < routineCoverage.size(); i++) {
				hash = routineCoverage.get(i);
				if (hash == 0)
					continue;

				nextSpan = establishRoutineSpan(hash);
				if (currentSpan == nextSpan) {
					end = i;
					continue;
				}

				currentSpan.addSpan(lines, start, end);

				start = end + 1;
				currentSpan = nextSpan;
			}
			currentSpan.addSpan(lines, start, i);
		}

		RoutineSpan establishRoutineSpan(int hash) {
			RoutineSpan span = routineSpans.get(hash);
			if (span == null) {
				span = new RoutineSpan(hash, phpFile.toPath());
				routineSpans.put(hash, span);
			}
			return span;
		}
	}

	private final ScriptDatasetLoader cfgLoader = new ScriptDatasetLoader();

	private final Map<Integer, RoutineSpan> routineSpans = new HashMap<Integer, RoutineSpan>();

	void load(File routineCatalog, File phpSourceDir, File dataset) throws NumberFormatException, IOException {
		if (!(routineCatalog.exists() && routineCatalog.isFile()))
			throw new AnalysisException("Cannot find routine catalog '%s'", routineCatalog.getAbsolutePath());
		if (!(phpSourceDir.exists() && phpSourceDir.isDirectory()))
			throw new AnalysisException("Cannot find PHP source directory '%s'", phpSourceDir.getAbsolutePath());
		if (!(dataset.exists() && dataset.isFile()))
			throw new AnalysisException("Cannot find dataset file '%s'", dataset.getAbsolutePath());

		Map<Path, ApplicationFile> catalog = new HashMap<Path, ApplicationFile>();
		BufferedReader catalogReader = new BufferedReader(new FileReader(routineCatalog));
		while (catalogReader.ready()) {
			StringTokenizer tokenizer = new StringTokenizer(catalogReader.readLine(), " |");
			int hash = Integer.parseInt(tokenizer.nextToken().substring(2), 16);
			File phpFile = new File(phpSourceDir, tokenizer.nextToken());
			ApplicationFile appFile = catalog.get(phpFile.toPath());
			if (appFile == null) {
				appFile = new ApplicationFile(phpFile);
				catalog.put(phpFile.toPath(), appFile);
			}
			appFile.routines.add(hash);
		}
		catalogReader.close();

		ScriptFlowGraph cfg = new ScriptFlowGraph(Type.DATASET, dataset.getAbsolutePath(), false);
		cfgLoader.loadDataset(dataset, cfg);

		for (ApplicationFile appFile : catalog.values()) {
			appFile.installRoutineSpans(cfg);
		}
	}

	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder();
		buffer.append(String.format("RoutineLineMap of %d routines\n", routineSpans.size()));
		for (RoutineSpan span : routineSpans.values()) {
			span.print(buffer);
		}
		return buffer.toString();
	}
}
