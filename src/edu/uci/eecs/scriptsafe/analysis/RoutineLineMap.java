package edu.uci.eecs.scriptsafe.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptDatasetLoader;
import edu.uci.eecs.scriptsafe.merge.graph.loader.ScriptGraphDataFiles.Type;

public class RoutineLineMap {

	private static class CodeLine {
		final int lineNumber; // N.B.: based from 1
		final String code;

		CodeLine(int lineNumber, String code) {
			this.lineNumber = lineNumber;
			this.code = code;
		}
	}

	private static class RoutineSpan {

		static final Set<String> wordSetBuilder = new HashSet<String>();

		final int hash;
		final Path filePath;
		final List<CodeLine> code = new ArrayList<CodeLine>(); // N.B.: based from 0

		List<String> words = null;

		RoutineSpan(int hash, Path filePath) {
			this.hash = hash;
			this.filePath = filePath;
		}

		void addSpan(List<String> codeLines, int start, int end /* exclusive */) {
			words = null;
			for (int i = start; i < end; i++)
				code.add(new CodeLine(i, codeLines.get(i)));
		}

		List<String> getWords() {
			parseWords();
			return words;
		}

		void parseWords() {
			if (words != null)
				return;

			StringBuilder buffer = new StringBuilder();
			char c;
			for (CodeLine line : code) {
				for (int i = 0; i < line.code.length(); i++) {
					c = line.code.charAt(i);
					if (buffer.length() == 0) {
						if (Character.isJavaIdentifierStart(c)) {
							buffer.append(c);
						}
					} else {
						if (Character.isJavaIdentifierPart(c)) {
							buffer.append(c);
						} else {
							wordSetBuilder.add(buffer.toString());
							buffer.setLength(0);
						}
					}
				}
				if (buffer.length() > 0)
					wordSetBuilder.add(buffer.toString());
			}
			words = new ArrayList<String>(wordSetBuilder);
			wordSetBuilder.clear();
		}

		void print(StringBuilder buffer) {
			if (code.isEmpty()) {
				buffer.append(String.format("Routine 0x%x in %s:empty\n", hash, filePath));
			} else {
				buffer.append(String.format("Routine 0x%x in %s:%d-%d\n", hash, filePath, code.get(0).lineNumber,
						code.get(code.size() - 1).lineNumber));
				for (CodeLine line : code)
					buffer.append(String.format("\t%04d: %s\n", line.lineNumber + 1, line.code));

				parseWords();

				buffer.append("\tWord bag: {");
				for (String word : words) {
					buffer.append(word);
					buffer.append("|");
				}
				if (!words.isEmpty())
					buffer.setLength(buffer.length() - 1);
				buffer.append("}\n");
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
			lines.add(""); // file ending in html will have opcodes off the end
			in.close();
		}

		void installRoutineSpans(ScriptFlowGraph cfg) {
			if (lines.isEmpty())
				return;

			List<Integer> lineCoverage = new ArrayList<Integer>();
			// List<Integer> Coverage = new ArrayList<Integer>();
			for (int i = 0; i < (lines.size() + 1); i++)
				lineCoverage.add(0);
			for (Integer hash : routines) {
				ScriptRoutineGraph routine = cfg.getRoutine(hash);

				boolean isAnonymous = cfg.edges.getMinUserLevel(routine.hash) < 2;

				for (ScriptNode node : routine.getNodes()) {
					if (node.lineNumber > (lineCoverage.size() + 1)) {
						Log.error("Node with opcode 0x%x in 0x%x has line number %d, but the file only has %d lines",
								node.opcode, routine.hash, node.lineNumber, lineCoverage.size());
						continue;
					}
					if (node.opcode == 0)
						continue; // there is no opcode zero

					/*
					 * if (node instanceof ScriptBranchNode) { ScriptBranchNode branch = (ScriptBranchNode) node; if
					 * (isAnonymous) { if (branch.getBranchUserLevel() >= 2) {
					 * Log.log(" === admin-only branch at index %d(%d) in anonymous routine 0x%x", node.index,
					 * routine.getNodeCount(), routine.hash); } else { //
					 * Log.log(" === anonymous branch at index %d(%d) in anonymous routine 0x%x", // node.index, //
					 * routine.getNodeCount(), routine.hash); } } else { if (branch.getBranchUserLevel() >= 2) { //
					 * Log.log(" === admin-only branch at index %d(%d) in admin routine 0x%x", node.index, //
					 * routine.getNodeCount(), routine.hash); } else {
					 * Log.log(" === anonymous branch at index %d(%d) in admin routine 0x%x", node.index,
					 * routine.getNodeCount(), routine.hash); } } }
					 */

					lineCoverage.set(Math.max(0, node.lineNumber - 1), routine.hash);
				}
			}

			int i = 0, hash, start, end = 0;
			RoutineSpan currentSpan = null, nextSpan;
			do {
				hash = lineCoverage.get(i++);
				if (i >= lineCoverage.size())
					return;
			} while (hash == 0);
			start = i - 1;
			end = start + 1;
			currentSpan = establishRoutineSpan(hash);

			for (; i < lineCoverage.size(); i++) {
				hash = lineCoverage.get(i);
				if (hash == 0)
					continue;

				nextSpan = establishRoutineSpan(hash);
				if (currentSpan == nextSpan) {
					end = i;
					continue;
				}

				currentSpan.addSpan(lines, start, end + 1);

				start = end + 1;
				end = i;
				currentSpan = nextSpan;
			}
			currentSpan.addSpan(lines, start, i - 1);
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

		for (ApplicationFile appFile : catalog.values())
			appFile.installRoutineSpans(cfg);
	}

	List<String> getWords(int hash) {
		RoutineSpan span = routineSpans.get(hash);
		if (span == null)
			return Collections.EMPTY_LIST;
		else
			return span.getWords();
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
