package edu.uci.eecs.scriptsafe.analysis.dictionary;

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
import edu.uci.eecs.scriptsafe.analysis.AnalysisException;
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
			List<Integer> userLevelCoverage = new ArrayList<Integer>();
			for (int i = 0; i < (lines.size() + 1); i++) {
				lineCoverage.add(0);
				userLevelCoverage.add(ScriptNode.USER_LEVEL_TOP);
			}
			for (Integer hash : routines) {
				ScriptRoutineGraph routine = cfg.getRoutine(hash);

				int entryUserLevel = cfg.edges.getMinUserLevel(routine.hash);
				boolean changedUserLevel = false;

				List<Integer> userLevelPhi = new ArrayList<Integer>();
				for (ScriptNode node : routine.getNodes()) {
					userLevelPhi.add(ScriptNode.USER_LEVEL_TOP);
				}
				for (ScriptNode node : routine.getNodes()) {
					if (node.lineNumber > (lineCoverage.size() + 1)) {
						Log.error("Node with opcode 0x%x in 0x%x has line number %d, but the file only has %d lines",
								node.opcode, routine.hash, node.lineNumber, lineCoverage.size());
						continue;
					}
					if (node.opcode == 0)
						continue; // there is no opcode zero

					if (node instanceof ScriptBranchNode) {
						ScriptBranchNode branch = (ScriptBranchNode) node;
						if (branch.getBranchUserLevel() != entryUserLevel) {
							if (branch.getBranchUserLevel() != ScriptNode.USER_LEVEL_TOP) {
								int targetIndex = branch.getTargetIndex();
								if (targetIndex != ScriptBranchNode.UNASSIGNED_BRANCH_TARGET_ID) {
									userLevelPhi.set(targetIndex, branch.getBranchUserLevel());
									changedUserLevel = true;
									Log.message("Starting phi %d on node %d, line %d of 0x%x",
											branch.getBranchUserLevel(), targetIndex,
											routine.getNode(targetIndex).lineNumber, routine.hash);
								} else {
									Log.message("Skipping phi for branch with unknown target in 0x%x", routine.hash);
								}
							} else {
								Log.message("Skipping phi for branch with top user level in 0x%x", routine.hash);
							}
						}
					}

					lineCoverage.set(getLineIndex(node), routine.hash);
					userLevelCoverage.set(getLineIndex(node), entryUserLevel);
				}

				int iteration = 0, maxIterations = 50;
				while (changedUserLevel) {
					if (++iteration > maxIterations)
						break;
					changedUserLevel = false;
					for (int i = 0; i < userLevelPhi.size(); i++) {
						int phi = userLevelPhi.get(i);
						if (phi != ScriptNode.USER_LEVEL_TOP) {
							for (int j = i; j < routine.getNodeCount(); j++) {
								ScriptNode node = routine.getNode(j);
								if (userLevelCoverage.get(getLineIndex(node)) != phi) {
									userLevelCoverage.set(getLineIndex(node), phi);
									Log.message("Changing user level on line %d of 0x%x to %d", node.lineNumber,
											routine.hash, phi);
									changedUserLevel = true;
								}
								if (node instanceof ScriptBranchNode) {
									ScriptBranchNode branch = (ScriptBranchNode) node;
									int targetIndex = branch.getTargetIndex();
									if (targetIndex != ScriptBranchNode.UNASSIGNED_BRANCH_TARGET_ID) {
										int targetPhi = userLevelPhi.get(targetIndex);
										if (branch.getBranchUserLevel() < targetPhi) {
											userLevelPhi.set(targetIndex, branch.getBranchUserLevel());
											changedUserLevel = true;
											Log.message("Propagating phi %d on node %d, line %d of 0x%x",
													branch.getBranchUserLevel(), targetIndex,
													routine.getNode(targetIndex).lineNumber, routine.hash);
										}
										if (branch.isConditional()
												&& userLevelCoverage.get(getLineIndex(node)) < userLevelPhi.get(j + 1)) {
											userLevelPhi.set(j + 1, userLevelCoverage.get(getLineIndex(node)));
											changedUserLevel = true;
											Log.message("Propagating phi %d on node %d, line %d of 0x%x",
													userLevelCoverage.get(getLineIndex(node)), j + 1,
													routine.getNode(j + 1).lineNumber, routine.hash);
										}
										break;
									}
								}
							}
						}
					}
				}
			}

			int i = 0, hash, userLevel, start, end = 0;
			RoutineSpan currentSpan = null, nextSpan;
			do {
				hash = lineCoverage.get(i);
				userLevel = userLevelCoverage.get(i++);
				if (i >= lineCoverage.size())
					return;
			} while (hash == 0);
			start = i - 1;
			end = start + 1;
			currentSpan = establishRoutineSpan(hash, userLevel);

			for (; i < lineCoverage.size(); i++) {
				hash = lineCoverage.get(i);
				userLevel = userLevelCoverage.get(i);
				if (hash == 0)
					continue;

				nextSpan = establishRoutineSpan(hash, userLevel);
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

		int getLineIndex(ScriptNode node) {
			return Math.max(0, node.lineNumber - 1);
		}

		RoutineSpan establishRoutineSpan(int hash, int userLevel) {
			ColoredRoutineSpan key = new ColoredRoutineSpan(hash, userLevel >= 2);
			RoutineSpan span = routineSpans.get(key);
			if (span == null) {
				span = new RoutineSpan(hash, phpFile.toPath());
				routineSpans.put(key, span);
			}
			return span;
		}
	}

	private static class ColoredRoutineSpan {
		final int hash;
		final boolean isAdmin;

		ColoredRoutineSpan(int hash, boolean isAdmin) {
			this.hash = hash;
			this.isAdmin = isAdmin;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + hash;
			result = prime * result + (isAdmin ? 1231 : 1237);
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ColoredRoutineSpan other = (ColoredRoutineSpan) obj;
			if (hash != other.hash)
				return false;
			if (isAdmin != other.isAdmin)
				return false;
			return true;
		}
	}

	private final ScriptDatasetLoader cfgLoader = new ScriptDatasetLoader();

	private final Map<ColoredRoutineSpan, RoutineSpan> routineSpans = new HashMap<ColoredRoutineSpan, RoutineSpan>();

	public void load(File routineCatalog, File phpSourceDir, File dataset) throws NumberFormatException, IOException {
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
		cfgLoader.loadDataset(dataset, routineCatalog, cfg);

		for (ApplicationFile appFile : catalog.values())
			appFile.installRoutineSpans(cfg);
	}

	List<String> getWords(int hash) {
		List<String> words = new ArrayList<String>();
		ColoredRoutineSpan key = new ColoredRoutineSpan(hash, true);
		RoutineSpan span = routineSpans.get(key);
		if (span != null)
			words.addAll(span.getWords());
		key = new ColoredRoutineSpan(hash, false);
		span = routineSpans.get(key);
		if (span != null)
			words.addAll(span.getWords());
		return words;
	}

	List<String> getWords(int hash, boolean isAdmin) {
		ColoredRoutineSpan key = new ColoredRoutineSpan(hash, isAdmin);
		RoutineSpan span = routineSpans.get(key);
		if (span == null)
			return Collections.emptyList();
		else
			return Collections.unmodifiableList(span.getWords());
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
