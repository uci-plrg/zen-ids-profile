package edu.uci.eecs.scriptsafe.analysis.request;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uci.eecs.scriptsafe.feature.FeatureCrossValidationSets;

public class CrossValidationRequestGraph extends RequestGraph {

	/* N.B.: hashCode() and equals() do not account for the color */
	private static class RawEdge {
		final int fromRoutineHash;
		final int fromIndex;
		final int toRoutineHash;
		final boolean isAdmin;

		RawEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, boolean isAdmin) {
			this.fromRoutineHash = fromRoutineHash;
			this.fromIndex = fromIndex;
			this.toRoutineHash = toRoutineHash;
			this.isAdmin = isAdmin;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + fromIndex;
			result = prime * result + fromRoutineHash;
			result = prime * result + toRoutineHash;
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
			RawEdge other = (RawEdge) obj;
			if (fromIndex != other.fromIndex)
				return false;
			if (fromRoutineHash != other.fromRoutineHash)
				return false;
			if (toRoutineHash != other.toRoutineHash)
				return false;
			return true;
		}
	}

	private static class RawRequest {
		final int requestId;
		final File routineCatalog;

		final List<RawEdge> edges = new ArrayList<RawEdge>();

		RawRequest(int requestId, File routineCatalog) {
			this.requestId = requestId;
			this.routineCatalog = routineCatalog;
		}
	}

	private final FeatureCrossValidationSets kSets;
	private final List<RawRequest> rawRequestsByK[];

	private RawRequest currentRequest;

	@SuppressWarnings("unchecked")
	public CrossValidationRequestGraph(FeatureCrossValidationSets kSets) {
		this.kSets = kSets;

		rawRequestsByK = new List[kSets.getNumberOfSets()];
		for (int i = 0; i < rawRequestsByK.length; i++)
			rawRequestsByK[i] = new ArrayList<RawRequest>();
	}

	/* N.B.: construction expects the exact workflow of RequestGraphLoader */
	@Override
	void startRequest(int requestId, File routineCatalog) {
		currentRequest = new RawRequest(requestId, routineCatalog);
		rawRequestsByK[kSets.getK(requestId)].add(currentRequest);
	}

	@Override
	void addEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, int userLevel, File routineCatalog)
			throws NumberFormatException, IOException {
		currentRequest.edges.add(new RawEdge(fromRoutineHash, fromIndex, toRoutineHash, userLevel > 2));
	}

	public void train(int k) throws NumberFormatException, IOException {
		for (RawRequest request : rawRequestsByK[k]) {
			for (RawEdge edge : request.edges) {
				super.addEdge(edge.fromRoutineHash, edge.fromIndex, edge.toRoutineHash, edge.isAdmin ? 10 : 0,
						request.routineCatalog);
			}
		}
	}

	public ByteBuffer getDelta(int k) {
		Set<RawEdge> newEdges = new HashSet<RawEdge>();
		for (RawRequest request : rawRequestsByK[k]) {
			for (RawEdge rawEdge : request.edges) {
				RequestEdgeSummary edge = getEdge(rawEdge.fromRoutineHash, rawEdge.fromIndex, rawEdge.toRoutineHash);
				if (edge == null || (!rawEdge.isAdmin && edge.getAnonymousCount() == 0))
					newEdges.add(rawEdge);
			}
		}
		
		ByteBuffer buffer = ByteBuffer.allocate(11 * newEdges.size());
		for (RawEdge newEdge : newEdges) {
			buffer.putInt(newEdge.fromRoutineHash);
			buffer.putShort((short) newEdge.fromIndex);
			buffer.putInt(newEdge.toRoutineHash);
			buffer.put((byte) (newEdge.isAdmin ? 1 : 0));
		}
		return buffer;
	}

	public int getNumberOfSets() {
		return rawRequestsByK.length;
	}
}
