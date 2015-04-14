package edu.uci.eecs.scriptsafe.merge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

import edu.uci.eecs.crowdsafe.common.log.Log;

public class ScriptMergeWatchList {

	private static final ScriptMergeWatchList INSTANCE = new ScriptMergeWatchList();

	public static ScriptMergeWatchList getInstance() {
		return INSTANCE;
	}

	private static class BranchSource {
		long routineHash;
		int nodeIndex;

		public BranchSource(long routineHash, int nodeIndex) {
			this.routineHash = routineHash;
			this.nodeIndex = nodeIndex;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + nodeIndex;
			result = prime * result + (int) (routineHash ^ (routineHash >>> 32));
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
			if (routineHash != other.routineHash)
				return false;
			return true;
		}
	}

	public enum Category {
		NODE_USER_LEVEL("ul"),
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

	private Set<Integer> routines = new HashSet<Integer>();
	private Set<BranchSource> branchpoints = new HashSet<BranchSource>();
	private Set<Category> activeCategories = EnumSet.noneOf(Category.class);

	public static boolean watch(int routineHash) {
		return getInstance().isRoutineActive(routineHash);
	}

	public static boolean watchAny(int routineHash, int nodeIndex) {
		return getInstance().isRoutineActive(routineHash) || getInstance().isBranchpointActive(routineHash, nodeIndex);
	}

	public static boolean watchBranchpoint(int routineHash, int nodeIndex) {
		return getInstance().isBranchpointActive(routineHash, nodeIndex);
	}

	public void loadFromFile(File watchlist) throws NumberFormatException, IOException {
		String line;
		int routineHash, nodeIndex, split;
		BufferedReader in = new BufferedReader(new FileReader(watchlist));
		while (in.ready()) {
			line = in.readLine();
			if (line.startsWith("0x"))
				line = line.substring(2);
			split = line.indexOf('|');
			if (split < 0) {
				routines.add(Integer.parseInt(line, 16));
			} else {
				routineHash = Integer.parseInt(line.substring(0, split), 16);
				nodeIndex = Integer.parseInt(line.substring(split + 1));
				branchpoints.add(new BranchSource(routineHash, nodeIndex));
			}
		}
		in.close();

		Log.message("Watching routines %s", routines);
		Log.message("Watching branchpoints %s", branchpoints);
	}

	public boolean isActive(Category category) {
		return activeCategories.contains(category);
	}

	public void activateCategories(String codeList) {
		StringTokenizer tokens = new StringTokenizer(codeList, ",");
		while (tokens.hasMoreTokens()) {
			activeCategories.add(Category.forCode(tokens.nextToken()));
		}

		Log.log("Watching categories %s", activeCategories);
	}

	private boolean isRoutineActive(int routineHash) {
		return routines.contains(routineHash);
	}

	private boolean isBranchpointActive(int routineHash, int nodeIndex) {
		lookup.routineHash = routineHash;
		lookup.nodeIndex = nodeIndex;

		return branchpoints.contains(lookup);
	}
}
