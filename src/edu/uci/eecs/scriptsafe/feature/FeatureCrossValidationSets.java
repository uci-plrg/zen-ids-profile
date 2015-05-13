package edu.uci.eecs.scriptsafe.feature;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeatureCrossValidationSets {

	final Map<Integer, Integer> kByRequestId = new HashMap<Integer, Integer>();
	final List<List<Integer>> crossValidationGroups = new ArrayList<List<Integer>>();
	int setCount = 0;

	public FeatureCrossValidationSets(File crossValidationFile) throws IOException {
		load(crossValidationFile);
	}

	void load(File crossValidationFile) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(crossValidationFile));
		try {
			while (in.ready()) {
				String kLine = in.readLine();
				List<Integer> k = new ArrayList<Integer>();
				for (String requestIdString : kLine.split(",")) {
					try {
						int requestId = Integer.parseInt(requestIdString);
						kByRequestId.put(requestId, setCount);
						k.add(requestId);
					} catch (NumberFormatException e) {
						// skip it
					}
				}
				setCount++;
				crossValidationGroups.add(k);
			}
		} finally {
			in.close();
		}
	}

	public int getNumberOfSets() {
		return setCount;
	}

	public int getK(int requestId) {
		return kByRequestId.get(requestId);
	}
}
