package edu.uci.eecs.scriptsafe.merge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptMergeWatchList {

	private static final ScriptMergeWatchList INSTANCE = new ScriptMergeWatchList();

	public static ScriptMergeWatchList getInstance() {
		return INSTANCE;
	}

	private static class BranchSource {
		long routineIndex;
		int nodeIndex;

		public BranchSource(long routineIndex, int nodeIndex) {
			this.routineIndex = routineIndex;
			this.nodeIndex = nodeIndex;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + nodeIndex;
			result = prime * result + (int) (routineIndex ^ (routineIndex >>> 32));
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
			BranchSource other = (BranchSource) obj;
			if (nodeIndex != other.nodeIndex)
				return false;
			if (routineIndex != other.routineIndex)
				return false;
			return true;
		}
	}

	public enum Category {
		OPCODE_EDGE("oe"),
		ROUTINE("r"),
		ROUTINE_EDGE("re"),
		EXCEPTION_EDGE("ee");

		public final String code;

		private Category(String code) {
			this.code = code;
		}

		public static Category forCode(String code) {
			for (Category category : Category.values()) {
				if (category.code.equals(code.toLowerCase().trim()))
					return category;
			}
			return null;
		}
	}

	private final BranchSource lookup = new BranchSource(0L, 0);

	private Set<BranchSource> sources = new HashSet<BranchSource>();
	private Set<Category> activeCategories = EnumSet.noneOf(Category.class);

	public void loadFromFile(File watchlist) throws NumberFormatException, IOException {
		String line;
		int routineHash, nodeIndex, split;
		BufferedReader in = new BufferedReader(new FileReader(watchlist));
		while (in.ready()) {
			line = in.readLine();
			split = line.indexOf('|');
			if (split < 0)
				continue;
			line = line.substring(split + 1);
			split = line.indexOf('|');
			if (split < 0)
				continue;
			routineHash = (int) Long.parseLong(line.substring(0, split), 16);
			nodeIndex = Integer.parseInt(line.substring(split + 1));
			sources.add(new BranchSource(routineHash, nodeIndex));
		}
		in.close();
	}

	public boolean isActive(Category category) {
		return activeCategories.contains(category);
	}

	public void activateCategories(String codeList) {
		StringTokenizer tokens = new StringTokenizer(codeList, ",");
		while (tokens.hasMoreTokens()) {
			activeCategories.add(Category.forCode(tokens.nextToken()));
		}
	}

	public boolean watch(int routineIndex, int nodeIndex) {
		lookup.routineIndex = routineIndex;
		lookup.nodeIndex = nodeIndex;

		return sources.contains(lookup);
	}
}
