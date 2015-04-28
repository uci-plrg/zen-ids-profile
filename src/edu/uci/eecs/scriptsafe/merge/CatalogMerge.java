package edu.uci.eecs.scriptsafe.merge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class CatalogMerge {

	private File left;
	private File right;

	private Map<String, String> routines = new HashMap<String, String>();

	public CatalogMerge(File left, File right) {
		this.left = left;
		this.right = right;
	}

	public void merge() throws IOException {
		BufferedReader inLeft = new BufferedReader(new FileReader(left));
		BufferedReader inRight = new BufferedReader(new FileReader(right));
		String line, hash, name, duplicateName;

		try {
			while (inLeft.ready()) {
				line = inLeft.readLine();
				hash = line.substring(0, 10);
				name = line.substring(11);

				duplicateName = routines.get(hash);
				if (duplicateName == null) {
					routines.put(hash, name);
				} else {
					if (!duplicateName.equals(name))
						throw new MergeException("Hash collision on %s: %s vs. %s", hash, name, duplicateName);
				}
			}
			while (inRight.ready()) {
				line = inRight.readLine();
				hash = line.substring(0, 10);
				name = line.substring(11);

				duplicateName = routines.get(hash);
				if (duplicateName == null) {
					routines.put(hash, name);
				} else {
					if (!duplicateName.equals(name))
						throw new MergeException("Hash collision on %s: %s vs. %s", hash, name, duplicateName);
				}
			}
		} finally {
			inLeft.close();
			inRight.close();
		}
	}

	public void generateCatalog(File outputFile) throws IOException {
		BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
		Map<String, String> sorter = new TreeMap<String, String>();
		for (Map.Entry<String, String> entry : routines.entrySet())
			sorter.put(entry.getValue(), entry.getKey());
		for (Map.Entry<String, String> entry : sorter.entrySet())
			out.write(String.format("%s %s\n", entry.getValue(), entry.getKey()));
		out.flush();
		out.close();
	}
}
