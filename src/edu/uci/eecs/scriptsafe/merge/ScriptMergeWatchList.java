package edu.uci.eecs.scriptsafe.merge;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

public class ScriptMergeWatchList {

	private static final ScriptMergeWatchList INSTANCE = new ScriptMergeWatchList();

	public static ScriptMergeWatchList getInstance() {
		return INSTANCE;
	}

	private static class BranchSource {
		long routineId;
		int nodeIndex;

		public BranchSource(long routineId, int nodeIndex) {
			this.routineId = routineId;
			this.nodeIndex = nodeIndex;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + nodeIndex;
			result = prime * result + (int) (routineId ^ (routineId >>> 32));
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
			if (routineId != other.routineId)
				return false;
			return true;
		}
	}

	private final BranchSource lookup = new BranchSource(0L, 0);

	private Set<BranchSource> sources = new HashSet<BranchSource>();

	public void loadFromFile(File watchlist) throws NumberFormatException, IOException {
		String line;
		int unitHash, routineHash, nodeIndex, split;
		BufferedReader in = new BufferedReader(new FileReader(watchlist));
		while (in.ready()) {
			line = in.readLine();
			split = line.indexOf('|');
			if (split < 0)
				continue;
			unitHash = (int) Long.parseLong(line.substring(0, split), 16);
			line = line.substring(split + 1);
			split = line.indexOf('|');
			if (split < 0)
				continue;
			routineHash = (int) Long.parseLong(line.substring(0, split), 16);
			nodeIndex = Integer.parseInt(line.substring(split + 1));
			sources.add(new BranchSource(ScriptRoutineGraph.constructId(unitHash, routineHash), nodeIndex));
		}
		in.close();
	}

	public boolean watch(long routineId, int nodeIndex) {
		lookup.routineId = routineId;
		lookup.nodeIndex = nodeIndex;

		return sources.contains(lookup);
	}
}
