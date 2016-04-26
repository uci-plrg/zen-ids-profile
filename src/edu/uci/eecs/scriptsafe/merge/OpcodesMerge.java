package edu.uci.eecs.scriptsafe.merge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class OpcodesMerge {

	private File left;
	private File right;

	Set<String> routineNames = new HashSet<String>();

	public OpcodesMerge(File left, File right) {
		this.left = left;
		this.right = right;
	}

	public void merge(File outputFile) throws NumberFormatException, IOException {
		if (!right.getAbsolutePath().equals(outputFile.getAbsolutePath()))
			Files.copy(new FileInputStream(right), Paths.get(outputFile.getPath()));

		String line;
		BufferedReader in;

		if (outputFile.exists()) {
			in = new BufferedReader(new InputStreamReader(new FileInputStream(outputFile)));
			while ((line = in.readLine()) != null) {
				if (line.startsWith(" === "))
					routineNames.add(line);
			}
			in.close();
		}

		boolean appendRoutine = false;
		in = new BufferedReader(new InputStreamReader(new FileInputStream(left)));
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile, true)));
		while ((line = in.readLine()) != null) {
			if (line.startsWith(" === ")) {
				appendRoutine = !routineNames.contains(line);
				if (appendRoutine)
					routineNames.add(line);
			}
			if (appendRoutine)
				out.write(line);
		}
		in.close();
		out.flush();
		out.close();
	}
}
