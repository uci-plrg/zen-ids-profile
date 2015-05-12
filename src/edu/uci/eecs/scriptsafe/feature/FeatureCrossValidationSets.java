package edu.uci.eecs.scriptsafe.feature;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FeatureCrossValidationSets {

	final List<List<Integer>> crossValidationGroups = new ArrayList<List<Integer>>();
	final Set<Integer> includedRequestIds = new HashSet<Integer>();

	public FeatureCrossValidationSets(File crossValidationFile) throws IOException {
		load(crossValidationFile);
	}

	void load(File crossValidationFile) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(crossValidationFile));
		try {
			while (in.ready()) {
				String kLine = in.readLine();
				List<Integer> k = new ArrayList<Integer>();
				for (String requestId : kLine.split(",")) {
					try {
						k.add(Integer.parseInt(requestId));
					} catch (NumberFormatException e) {
						// skip it
					}
				}
				crossValidationGroups.add(k);
			}
		} finally {
			in.close();
		}
	}

	Set<Integer> augmentCrossValidation(int k) {
		includedRequestIds.addAll(crossValidationGroups.get(k));
		return includedRequestIds;
	}
}
