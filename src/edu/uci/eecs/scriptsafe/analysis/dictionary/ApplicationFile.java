package edu.uci.eecs.scriptsafe.analysis.dictionary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptBranchNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptFlowGraph;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptNode;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

class ApplicationFile {
	final File phpFile;
	final List<String> lines = new ArrayList<String>();
	final List<Integer> routines = new ArrayList<Integer>();

	final List<Integer> lineCoverage = new ArrayList<Integer>();
	final List<Integer> userLevelCoverage = new ArrayList<Integer>();

	ApplicationFile(File phpFile) throws IOException {
		this.phpFile = phpFile;

		BufferedReader in = new BufferedReader(new FileReader(phpFile));
		while (in.ready()) {
			lines.add(in.readLine());
		}
		lines.add(""); // file ending in html will have opcodes off the end
		in.close();
	}

	void mapLineCoverage(ScriptFlowGraph cfg) {
		for (int i = 0; i < (lines.size() + 1); i++) {
			lineCoverage.add(0);
			userLevelCoverage.add(ScriptNode.USER_LEVEL_TOP);
		}
		for (Integer hash : routines) {
			ScriptRoutineGraph routine = cfg.getRoutine(hash);
			
			if (routine == null) {
				Log.error("Error: missing routine for hash 0x%x!", hash);
				continue;
			}

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
	}
	
	int getLineIndex(ScriptNode node) {
		return Math.max(0, node.lineNumber - 1);
	}
}
