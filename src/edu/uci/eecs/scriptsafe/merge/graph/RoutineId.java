package edu.uci.eecs.scriptsafe.merge.graph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import edu.uci.eecs.scriptsafe.analysis.AnalysisException;

public class RoutineId {

	public static class Cache {

		public static final Cache INSTANCE = new Cache();

		private final Map<Integer, RoutineId> routineIds = new HashMap<Integer, RoutineId>();
		private final Map<Path, List<Integer>> routinesBySourceFile = new TreeMap<Path, List<Integer>>();

		private Cache() {
		}

		public RoutineId getId(int routineHash) {
			if (ScriptRoutineGraph.isDynamicRoutine(routineHash))
				return DYNAMIC_ID;
			return routineIds.get(routineHash);
		}

		public List<Integer> getRoutinesInFile(Path file) {
			return routinesBySourceFile.get(file);
		}

		public Iterable<Path> getAllKnownFiles() {
			return routinesBySourceFile.keySet();
		}

		/**
		 * Get the `RoutineId` for `routineHash`, loading it from the supplied `catalog` if necessary.
		 */
		public RoutineId getId(File catalog, int routineHash) throws NumberFormatException, IOException {
			RoutineId routineId = getId(routineHash);
			if (routineId == null) {
				load(catalog);
				routineId = routineIds.get(routineHash);
			}
			return routineId;
		}

		public void load(File catalog) throws NumberFormatException, IOException {
			BufferedReader in = new BufferedReader(new FileReader(catalog));
			try {
				String line;
				int space;
				while ((line = in.readLine()) != null) {
					space = line.trim().indexOf(" ");
					if (space < 0)
						continue;
					int hash = Integer.parseInt(line.substring(2, space), 16);
					String id = line.substring(space + 1);
					int pipeIndex = id.indexOf('|');
					if (pipeIndex < 0) {
						throw new AnalysisException("Invalid routine id '%s' found in routine catalog %s", id,
								catalog.getAbsolutePath());
					}
					Path file = new File(id.substring(0, pipeIndex)).toPath();
					String name = id.substring(pipeIndex + 1);
					routineIds.put(hash, new RoutineId(id, name, file));
					establishFileRoutines(file).add(hash);
				}
			} finally {
				in.close();
			}
		}

		private List<Integer> establishFileRoutines(Path sourceFile) {
			List<Integer> routines = routinesBySourceFile.get(sourceFile);
			if (routines == null) {
				routines = new ArrayList<Integer>();
				routinesBySourceFile.put(sourceFile, routines);
			}
			return routines;
		}
	}

	public static RoutineId ENTRY_ID = new RoutineId("<entry-point>", "<entry-point>", null);
	public static RoutineId DYNAMIC_ID = new RoutineId("<eval-routine>", "<eval-routine>", null);

	/**
	 * Fully qualified name as "<file-path>:<scope>:<routine-name>"
	 */
	public final String id;
	/**
	 * Just the "<scope>:<routine-name>", e.g. Custom_Image_Header:ajax_header_add() or
	 * wordpress/wp-admin/admin.php:<script-body>.
	 */
	public final String name;
	/**
	 * Relative path to the php file containing this routine.
	 */
	public Path sourceFile;

	private RoutineId(String id, String name, Path sourceFile) {
		this.id = id;
		this.name = name;
		this.sourceFile = sourceFile;
	}
}
