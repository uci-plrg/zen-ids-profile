package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import edu.uci.eecs.scriptsafe.analysis.request.RequestCallSiteSummary;
import edu.uci.eecs.scriptsafe.analysis.request.RequestEdgeSummary;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineEdge;
import edu.uci.eecs.scriptsafe.merge.graph.RoutineId;
import edu.uci.eecs.scriptsafe.merge.graph.ScriptRoutineGraph;

/**
 * <pre>
 * feature data: 
 * 
 * caller <role-counts>
 * calling sites <role-counts>
 * target <role-counts>
 * target file <role-counts>
 * target directory <role-counts>
 * word[0..n] <role-counts>
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
	private SourceWordList wordList = new SourceWordList(0.9f, 4f); // TODO: config

	FeatureResponseGenerator responseGenerator = new FeatureResponseGenerator();

	EdgeFeatureCollector() {
		responseGenerator.addField(callSiteCounts);
		responseGenerator.addField(callingSiteCounts);
		responseGenerator.addField(targetIncomingCounts);
		responseGenerator.addField(targetFileIncomingCounts);
		responseGenerator.addField(targetDirectoryIncomingCounts);
		responseGenerator.addField(wordList);
	}

	void setDataSource(FeatureDataSource dataSource) {
		this.dataSource = dataSource;
		wordList.setDataSource(dataSource);
	}

	ByteBuffer getFeatures(int fromRoutineHash, int fromOpcode, int toRoutineHash) {
		responseGenerator.resetAllFields();

		// TODO: filter by training set of edges (pretend others are not there)
		RequestCallSiteSummary callSite = dataSource.requestGraph.getCallSite(fromRoutineHash, fromOpcode);
		for (RequestEdgeSummary edge : callSite.getEdges())
			callSiteCounts.addCounts(edge);

		for (RoutineEdge edge : dataSource.dataset.edges.getIncomingEdges(toRoutineHash)) {
			callSite = dataSource.requestGraph.getCallSite(edge.getFromRoutineHash(), edge.getFromRoutineIndex());
			for (RequestEdgeSummary callingSiteSummary : callSite.getEdges())
				callingSiteCounts.addCounts(callingSiteSummary);
		}

		for (RoutineEdge edge : dataSource.dataset.edges.getIncomingEdges(toRoutineHash)) {
			targetIncomingCounts.addCounts(dataSource.requestGraph.getEdge(edge.getFromRoutineHash(),
					edge.getFromRoutineIndex(), toRoutineHash));
		}

		ScriptRoutineGraph routine = dataSource.dataset.getRoutine(fromRoutineHash);
		for (int routineInFileHash : RoutineId.Cache.INSTANCE.getRoutinesInFile(routine.id.sourceFile)) {
			for (RoutineEdge edge : dataSource.dataset.edges.getIncomingEdges(routineInFileHash)) {
				targetFileIncomingCounts.addCounts(dataSource.requestGraph.getEdge(edge.getFromRoutineHash(),
						edge.getFromRoutineIndex(), routineInFileHash));
			}
		}

		Path directory = routine.id.sourceFile.getParent();
		for (Path file : RoutineId.Cache.INSTANCE.getFilesInDirectory(directory)) {
			for (int routineInFileHash : RoutineId.Cache.INSTANCE.getRoutinesInFile(file)) {
				for (RoutineEdge edge : dataSource.dataset.edges.getIncomingEdges(routineInFileHash)) {
					targetDirectoryIncomingCounts.addCounts(dataSource.requestGraph.getEdge(edge.getFromRoutineHash(),
							edge.getFromRoutineIndex(), routineInFileHash));
				}
			}
		}

		wordList.evaluateRoutine(toRoutineHash);

		return responseGenerator.generateResponse();
	}
}
