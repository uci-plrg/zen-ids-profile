package edu.uci.eecs.scriptsafe.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import edu.uci.eecs.crowdsafe.common.log.Log;
import edu.uci.eecs.crowdsafe.common.util.ArgumentStack;
import edu.uci.eecs.crowdsafe.common.util.OptionArgumentMap;

public class EvolutionTriggerGenerator {

	private static class DatabaseTable {
		final String tableName;
		final String keyName;
		final List<String> dataColumnNames = new ArrayList<String>();

		final String insertTriggerName;
		final String updateTriggerName;

		public DatabaseTable(String tableName, String keyName) {
			this.tableName = tableName;
			this.keyName = keyName;

			insertTriggerName = "evo_insert_" + tableName;
			updateTriggerName = "evo_update_" + tableName;
		}
	}

	private static class TriggerGeneratorException extends RuntimeException {
		public TriggerGeneratorException(String format, Object... args) {
			super(String.format(format, args));
		}
	}

	private static class LinePrintStream extends PrintStream {
		public LinePrintStream(File file) throws FileNotFoundException {
			super(file);
		}

		@Override
		public PrintStream format(String format, Object... args) {
			return super.format(format + "\n", args);
		}
	}

	public static final OptionArgumentMap.StringOption schemaFilePath = OptionArgumentMap.createStringOption('s');
	public static final OptionArgumentMap.StringOption outputFilePath = OptionArgumentMap.createStringOption('o');

	private final ArgumentStack args;
	private final OptionArgumentMap argMap;

	private BufferedReader in;
	private PrintStream out;

	private List<DatabaseTable> tables = new ArrayList<DatabaseTable>();

	private EvolutionTriggerGenerator(ArgumentStack args) {
		this.args = args;
		argMap = new OptionArgumentMap(args, schemaFilePath, outputFilePath);
	}

	private void run() {
		try {
			Log.addOutput(System.out);
			argMap.parseOptions();

			if (!schemaFilePath.hasValue()) {
				printUsage();
				return;
			}

			File schemaFile = new File(schemaFilePath.getValue());
			if (!schemaFile.exists()) {
				Log.error("Failed to locate the schema file %s", schemaFile.getAbsolutePath());
				return;
			}

			File outputFile = new File(outputFilePath.getValue());
			if (outputFile.getParentFile() == null || !outputFile.getParentFile().exists()) {
				Log.error("Failed to create the output file %s. Cannot find directory %s.", outputFile
						.getAbsolutePath(), outputFile.getParentFile() == null ? "null" : outputFile.getParentFile()
						.getAbsolutePath());
			}

			in = new BufferedReader(new FileReader(schemaFile));
			out = new LinePrintStream(outputFile);

			try {
				loadSchema();
				generateTriggers();
			} finally {
				in.close();
				out.flush();
				out.close();
			}
		} catch (Throwable t) {
			Log.log(t);
		}
	}

	private void loadSchema() throws IOException {
		String line;

		findHeader(in, "tables");

		List<String> tableNames = new ArrayList<String>();
		while ((line = in.readLine()) != null) {
			line = line.trim();
			if (line.length() == 0)
				break;
			tableNames.add(line);
		}

		String columnFields[];
		DatabaseTable table;
		tables: for (String tableName : tableNames) {
			findHeader(in, tableName);
			in.readLine(); // skip the row of labels

			columnFields = in.readLine().split("\t");
			if (!columnFields[3].equals("PRI")) {
				throw new TriggerGeneratorException("Failed to find the primary key for table %s. Token is '%s'",
						tableName, columnFields[3]);
			}

			table = new DatabaseTable(tableName, columnFields[0]);
			while ((line = in.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0)
					break;

				columnFields = line.split("\t");
				if (columnFields.length > 3 && columnFields[3].equals("PRI"))
					continue tables; // skip multi-key tables

				table.dataColumnNames.add(line.substring(0, line.indexOf('\t')));
			}
			tables.add(table);
		}
	}

	private void generateTriggers() {
		for (DatabaseTable table : tables) {
			out.format("DROP TRIGGER IF EXISTS %s;", table.updateTriggerName);
			out.format("DROP TRIGGER IF EXISTS %s;", table.insertTriggerName);
		}

		out.println();
		out.println("DELIMITER $$");

		for (DatabaseTable table : tables) {
			createInsertTrigger(table);
			out.println();
			createUpdateTrigger(table);
			out.println();
		}

		out.println("DELIMITER ;");
	}

	private void createInsertTrigger(DatabaseTable table) {
		out.format("CREATE TRIGGER %s AFTER INSERT ON %s", table.insertTriggerName, table.tableName);
		out.format("FOR EACH ROW");
		out.format("BEGIN");
		for (String columnName : table.dataColumnNames) {
			out.format("  REPLACE INTO opmon_evolution_staging VALUES ('%s', '%s', NEW.%s, connection_id());",
					table.tableName, columnName, table.keyName);
		}
		out.format("END $$");
	}

	private void createUpdateTrigger(DatabaseTable table) {
		out.format("CREATE TRIGGER %s AFTER UPDATE ON %s", table.updateTriggerName, table.tableName);
		out.format("FOR EACH ROW");
		out.format("BEGIN");
		for (String columnName : table.dataColumnNames) {
			out.format("  IF NEW.%s != OLD.%s THEN", columnName, columnName);
			out.format("    REPLACE INTO opmon_evolution_staging VALUES ('%s', '%s', NEW.%s, connection_id());",
					table.tableName, columnName, table.keyName);
			out.format("  END IF;");
		}
		out.format("END $$");
	}

	private void findHeader(BufferedReader in, String header) throws IOException {
		String line;
		while ((line = in.readLine()) != null) {
			line = line.trim();
			if (line.equals(header)) {
				in.readLine(); // skip separator line
				return;
			}
		}
		throw new TriggerGeneratorException("Failed to find the header '%s'", header);
	}

	private void printUsage() {
		System.err.println(String.format("Usage: %s -s <schema-file> -o <output-file>", getClass().getSimpleName()));
	}

	public static void main(String[] args) {
		ArgumentStack stack = new ArgumentStack(args);
		EvolutionTriggerGenerator generator = new EvolutionTriggerGenerator(stack);
		generator.run();
	}
}

/**
 * <pre> 
  
Sample input file (generated from bash using `mysql -e "query ..."` in a loop over `mysql -e "show tables"`):
 
tables
------
wp_commentmeta
wp_comments
wp_links
wp_options
wp_postmeta
wp_posts
wp_term_relationships
wp_term_taxonomy
wp_terms
wp_usermeta
wp_users

wp_commentmeta
-----------
Field   Type    Null    Key     Default Extra
meta_id bigint(20) unsigned     NO      PRI     NULL    auto_increment
comment_id      bigint(20) unsigned     NO      MUL     0
meta_key        varchar(255)    YES     MUL     NULL
meta_value      longtext        YES             NULL

Sample output file:

DROP TRIGGER IF EXISTS evo_insert_wp_commentmeta;
DROP TRIGGER IF EXISTS evo_update_wp_commentmeta;

DELIMITER $$

CREATE TRIGGER evo_insert_wp_commentmeta AFTER INSERT ON wp_commentmeta
FOR EACH ROW
BEGIN
  REPLACE INTO opmon_evolution_staging VALUES ('wp_commentmeta', 'comment_id', NEW.meta_id, connection_id());
  REPLACE INTO opmon_evolution_staging VALUES ('wp_commentmeta', 'meta_key', NEW.meta_id, connection_id());
  REPLACE INTO opmon_evolution_staging VALUES ('wp_commentmeta', 'meta_value', NEW.meta_id, connection_id());
END $$

CREATE TRIGGER evo_update_wp_commentmeta AFTER UPDATE ON wp_commentmeta
FOR EACH ROW
BEGIN
  IF NEW.comment_id != OLD.comment_id THEN
    REPLACE INTO opmon_evolution_staging VALUES ('wp_commentmeta', 'comment_id', NEW.meta_id, connection_id());
  END IF;
  IF NEW.meta_key != OLD.meta_key THEN
    REPLACE INTO opmon_evolution_staging VALUES ('wp_commentmeta', 'meta_key', NEW.meta_id, connection_id());
  END IF;
  IF NEW.meta_value != OLD.meta_value THEN
    REPLACE INTO opmon_evolution_staging VALUES ('wp_commentmeta', 'meta_value', NEW.meta_id, connection_id());
  END IF;
END $$
 */
