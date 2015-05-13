package edu.uci.eecs.scriptsafe.analysis.request;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.uci.eecs.scriptsafe.feature.FeatureCrossValidationSets;
import edu.uci.eecs.scriptsafe.feature.FeatureResponse;

public class CrossValidationRequestGraph extends RequestGraph {

	/* N.B.: hashCode() and equals() do not account for the color */
	private static class RawEdge {

		private static class EndpointKey {
			final int fromRoutineHash;
			final int fromIndex;
			final int toRoutineHash;

			EndpointKey(int fromRoutineHash, int fromIndex, int toRoutineHash) {
				this.fromRoutineHash = fromRoutineHash;
				this.fromIndex = fromIndex;
				this.toRoutineHash = toRoutineHash;
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
				EndpointKey other = (EndpointKey) obj;
				if (fromIndex != other.fromIndex)
					return false;
				if (fromRoutineHash != other.fromRoutineHash)
					return false;
				if (toRoutineHash != other.toRoutineHash)
					return false;
				return true;
			}
		}

		final EndpointKey key;
		final boolean isAdmin;

		RawEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, boolean isAdmin) {
			key = new EndpointKey(fromRoutineHash, fromIndex, toRoutineHash);
			this.isAdmin = isAdmin;
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
				super.addEdge(edge.key.fromRoutineHash, edge.key.fromIndex, edge.key.toRoutineHash, edge.isAdmin ? 10
						: 0, request.routineCatalog);
			}
		}
	}

	public ByteBuffer getDelta(int k) {
		Map<RawEdge.EndpointKey, RawEdge> newEdges = new HashMap<RawEdge.EndpointKey, RawEdge>();
		for (RawRequest request : rawRequestsByK[k]) {
			for (RawEdge rawEdge : request.edges) {
				RequestEdgeSummary edge = getEdge(rawEdge.key.fromRoutineHash, rawEdge.key.fromIndex,
						rawEdge.key.toRoutineHash);
				RawEdge newEdge = newEdges.get(rawEdge.key);
				if (edge == null) {
					if (newEdge == null || (newEdge.isAdmin && !rawEdge.isAdmin))
						newEdges.put(rawEdge.key, rawEdge);
				} else if (!rawEdge.isAdmin && edge.getAnonymousCount() == 0) {
					newEdges.put(rawEdge.key, rawEdge);
				}
			}
		}

		ByteBuffer buffer = FeatureResponse.OK.generateResponse(11 * newEdges.size());
		for (RawEdge newEdge : newEdges.values()) {
			buffer.putInt(newEdge.key.fromRoutineHash);
			buffer.putShort((short) newEdge.key.fromIndex);
			buffer.putInt(newEdge.key.toRoutineHash);
			buffer.put((byte) (newEdge.isAdmin ? 1 : 0));
		}
		return buffer;
	}

	public int getNumberOfSets() {
		return rawRequestsByK.length;
	}
}
