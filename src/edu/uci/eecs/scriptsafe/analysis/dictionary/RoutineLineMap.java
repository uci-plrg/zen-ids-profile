package edu.uci.eecs.scriptsafe.analysis.dictionary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

		static final WordAppearanceCount.SetBuilder wordSetBuilder = new WordAppearanceCount.SetBuilder();

		final int hash;
		final Path filePath;
		final List<CodeLine> code = new ArrayList<CodeLine>(); // N.B.: based from 0

		List<WordAppearanceCount> words = null;

		RoutineSpan(int hash, Path filePath) {
			this.hash = hash;
			this.filePath = filePath;
		}

		void addSpan(List<String> codeLines, int start, int end /* exclusive */) {
			words = null;
			for (int i = start; i < end; i++)
				code.add(new CodeLine(i, codeLines.get(i)));
		}

		List<WordAppearanceCount> getWords() {
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
							wordSetBuilder.addWordAppearance(buffer.toString());
							buffer.setLength(0);
						}
					}
				}
				if (buffer.length() > 0)
					wordSetBuilder.addWordAppearance(buffer.toString());
			}
			words = wordSetBuilder.serializeWords();
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
				for (WordAppearanceCount word : words) {
					buffer.append(word.word);
					if (word.getCount() > 1) {
						buffer.append(":");
						buffer.append(word.getCount());
					}
					buffer.append("|");
				}
				if (!words.isEmpty())
					buffer.setLength(buffer.length() - 1);
				buffer.append("}\n");
			}
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

	private static final WordAppearanceCount.SetBuilder wordListAggregator = new WordAppearanceCount.SetBuilder();

	private final ScriptDatasetLoader cfgLoader = new ScriptDatasetLoader();

	private final Map<ColoredRoutineSpan, RoutineSpan> routineSpans = new HashMap<ColoredRoutineSpan, RoutineSpan>();

	private Collection<ApplicationFile> inflate(File routineCatalog, File phpSourceDir, File dataset)
			throws NumberFormatException, IOException {
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
				appFile = new ApplicationFile(phpFile); /* loads the source code lines */
				catalog.put(phpFile.toPath(), appFile);
			}
			appFile.routines.add(hash);
		}
		catalogReader.close();

		return catalog.values();
	}
	
	void installRoutineSpans(ApplicationFile appFile, ScriptFlowGraph cfg) {
		if (appFile.lines.isEmpty())
			return;

		appFile.mapLineCoverage(cfg);

		int i = 0, hash, userLevel, start, end = 0;
		RoutineSpan currentSpan = null, nextSpan;
		do {
			hash = appFile.lineCoverage.get(i);
			userLevel = appFile.userLevelCoverage.get(i++);
			if (i >= appFile.lineCoverage.size())
				return;
		} while (hash == 0);
		start = i - 1;
		end = start + 1;
		currentSpan = establishRoutineSpan(appFile, hash, userLevel);

		for (; i < appFile.lineCoverage.size(); i++) {
			hash = appFile.lineCoverage.get(i);
			userLevel = appFile.userLevelCoverage.get(i);
			if (hash == 0)
				continue;

			nextSpan = establishRoutineSpan(appFile, hash, userLevel);
			if (currentSpan == nextSpan) {
				end = i;
				continue;
			}

			currentSpan.addSpan(appFile.lines, start, end + 1);

			start = end + 1;
			end = i;
			currentSpan = nextSpan;
		}
		currentSpan.addSpan(appFile.lines, start, i - 1);
	}

	RoutineSpan establishRoutineSpan(ApplicationFile appFile, int hash, int userLevel) {
		ColoredRoutineSpan key = new ColoredRoutineSpan(hash, userLevel >= 2);
		RoutineSpan span = routineSpans.get(key);
		if (span == null) {
			span = new RoutineSpan(hash, appFile.phpFile.toPath());
			routineSpans.put(key, span);
		}
		return span;
	}

	public void load(File routineCatalog, File phpSourceDir, File dataset) throws NumberFormatException, IOException {
		Collection<ApplicationFile> datasetFiles = inflate(routineCatalog, phpSourceDir, dataset);

		ScriptFlowGraph cfg = new ScriptFlowGraph(Type.DATASET, dataset.getAbsolutePath(), false);
		cfgLoader.loadDataset(dataset, routineCatalog, cfg, false);

		for (ApplicationFile appFile : datasetFiles)
			installRoutineSpans(appFile, cfg);
	}

	public Collection<ApplicationFile> countLines(File routineCatalog, File phpSourceDir, File dataset)
			throws NumberFormatException, IOException {
		Collection<ApplicationFile> datasetFiles = inflate(routineCatalog, phpSourceDir, dataset);

		ScriptFlowGraph cfg = new ScriptFlowGraph(Type.DATASET, dataset.getAbsolutePath(), false);
		cfgLoader.loadDataset(dataset, routineCatalog, cfg, false);

		for (ApplicationFile appFile : datasetFiles)
			appFile.mapLineCoverage(cfg);
		
		return datasetFiles;
	}

	public List<WordAppearanceCount> getWords(int hash) {
		ColoredRoutineSpan key = new ColoredRoutineSpan(hash, true);
		RoutineSpan span = routineSpans.get(key);
		if (span != null)
			wordListAggregator.addWordAppearances(span.getWords());
		key = new ColoredRoutineSpan(hash, false);
		span = routineSpans.get(key);
		if (span != null)
			wordListAggregator.addWordAppearances(span.getWords());
		return wordListAggregator.serializeWords();
	}

	public List<WordAppearanceCount> getWords(int hash, boolean isAdmin) {
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
