package edu.uci.plrg.cfi.php.feature;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import edu.uci.plrg.cfi.common.log.Log;
import edu.uci.plrg.cfi.php.analysis.request.RequestCallSiteSummary;
import edu.uci.plrg.cfi.php.analysis.request.RequestEdgeSummary;
import edu.uci.plrg.cfi.php.merge.graph.RoutineEdge;
import edu.uci.plrg.cfi.php.merge.graph.RoutineId;
import edu.uci.plrg.cfi.php.merge.graph.ScriptRoutineGraph;

/**
 * <pre>
 * feature data: 
 * 
 * caller <role-counts>
 * calling sites <role-counts>
 * target <role-counts>
 * target file <role-counts>
 * target directory <role-counts>
 * word[0..n]: <count-in-routine>, routine match <role-counts>, appearance <role-counts>
 * word[0..n] text
 * </pre>
 */
class EdgeFeatureCollector {

	private FeatureDataSource dataSource;

	private FeatureRoleCounts callSiteCounts = new FeatureRoleCounts();
	private FeatureRoleCounts callingSiteCounts = new FeatureRoleCounts();
	private FeatureRoleCounts targetIncomingCounts = new FeatureRoleCounts();
	private FeatureRoleCounts targetFileIncomingCounts = new FeatureRoleCounts();
	private FeatureRoleCounts targetDirectoryIncomingCounts = new FeatureRoleCounts();

	final FeatureResponseGenerator responseGenerator = new FeatureResponseGenerator();

	void setDataSource(FeatureDataSource dataSource) {
		this.dataSource = dataSource;

		responseGenerator.addField(callSiteCounts);
		responseGenerator.addField(callingSiteCounts);
		responseGenerator.addField(targetIncomingCounts);
		responseGenerator.addField(targetFileIncomingCounts);
		responseGenerator.addField(targetDirectoryIncomingCounts);
		responseGenerator.addField(dataSource.wordList.createWordMatchResponseField());
	}

	ByteBuffer getFeatures(int fromRoutineHash, int fromOpcode, int toRoutineHash) {
		responseGenerator.resetAllFields();

		RequestCallSiteSummary callSite = dataSource.trainingRequestGraph.getCallSite(fromRoutineHash, fromOpcode);
		if (callSite != null) {
			for (RequestEdgeSummary edge : callSite.getEdges())
				callSiteCounts.addCounts(edge);
		}

		for (RoutineEdge edge : dataSource.dataset.edges.getIncomingEdges(toRoutineHash)) {
			if (dataSource.trainingRequestGraph.getEdge(edge.getFromRoutineHash(), edge.getFromRoutineIndex(),
					edge.getToRoutineHash()) != null) { // filter edges outside the current training set
				callSite = dataSource.trainingRequestGraph.getCallSite(edge.getFromRoutineHash(),
						edge.getFromRoutineIndex());
				if (callSite != null) {
					for (RequestEdgeSummary callingSiteSummary : callSite.getEdges())
						callingSiteCounts.addCounts(callingSiteSummary);
				}
			}
		}

		for (RoutineEdge edge : dataSource.dataset.edges.getIncomingEdges(toRoutineHash)) {
			if (dataSource.trainingRequestGraph.getEdge(edge.getFromRoutineHash(), edge.getFromRoutineIndex(),
					edge.getToRoutineHash()) != null) { // filter edges outside the current training set
				targetIncomingCounts.addCounts(dataSource.trainingRequestGraph.getEdge(edge.getFromRoutineHash(),
						edge.getFromRoutineIndex(), toRoutineHash));
			}
		}

		ScriptRoutineGraph routine = dataSource.dataset.getRoutine(toRoutineHash);
		for (int routineInFileHash : RoutineId.Cache.INSTANCE.getRoutinesInFile(routine.id.sourceFile)) {
			for (RoutineEdge edge : dataSource.dataset.edges.getIncomingEdges(routineInFileHash)) {
				targetFileIncomingCounts.addCounts(dataSource.trainingRequestGraph.getEdge(edge.getFromRoutineHash(),
						edge.getFromRoutineIndex(), routineInFileHash));
			}
		}

		Path directory = routine.id.sourceFile.getParent();
		for (Path file : RoutineId.Cache.INSTANCE.getFilesInDirectory(directory)) {
			for (int routineInFileHash : RoutineId.Cache.INSTANCE.getRoutinesInFile(file)) {
				for (RoutineEdge edge : dataSource.dataset.edges.getIncomingEdges(routineInFileHash)) {
					targetDirectoryIncomingCounts.addCounts(dataSource.trainingRequestGraph.getEdge(
							edge.getFromRoutineHash(), edge.getFromRoutineIndex(), routineInFileHash));
				}
			}
		}

		dataSource.wordList.evaluateRoutine(toRoutineHash);

		return responseGenerator.generateResponse();
	}
}
