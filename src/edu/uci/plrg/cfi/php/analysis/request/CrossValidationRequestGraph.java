package edu.uci.plrg.cfi.php.analysis.request;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import edu.uci.plrg.cfi.php.feature.FeatureCrossValidationSets;
import edu.uci.plrg.cfi.php.feature.FeatureResponse;

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
		final File routineCatalog;

		RawEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, File routineCatalog, boolean isAdmin) {
			key = new EndpointKey(fromRoutineHash, fromIndex, toRoutineHash);
			this.routineCatalog = routineCatalog;
			this.isAdmin = isAdmin;
		}
	}

	private static class RequestGroup {
		final Map<RawEdge.EndpointKey, RawEdge> edges = new HashMap<RawEdge.EndpointKey, RawEdge>();
	}

	private final FeatureCrossValidationSets kSets;
	private final RequestGroup rawRequestsByK[];

	private RequestGroup currentGroup;

	public CrossValidationRequestGraph(FeatureCrossValidationSets kSets) {
		this.kSets = kSets;

		rawRequestsByK = new RequestGroup[kSets.getNumberOfSets()];
		for (int i = 0; i < rawRequestsByK.length; i++)
			rawRequestsByK[i] = new RequestGroup();
	}

	/* N.B.: construction expects the exact workflow of RequestGraphLoader */
	@Override
	public boolean startRequest(int requestId, File routineCatalog) {
		currentGroup = rawRequestsByK[kSets.getK(requestId)];
		return true;
	}

	@Override
	public boolean addEdge(int fromRoutineHash, int fromIndex, int toRoutineHash, int toIndex, int userLevel,
			File routineCatalog) throws NumberFormatException, IOException {
		RawEdge.EndpointKey key = new RawEdge.EndpointKey(fromRoutineHash, fromIndex, toRoutineHash);
		RawEdge existing = currentGroup.edges.get(key);
		if (existing == null || (userLevel < 2 && existing.isAdmin)) {
			currentGroup.edges.put(key, new RawEdge(fromRoutineHash, fromIndex, toRoutineHash, routineCatalog,
					userLevel >= 2));
		}
		return true;
	}

	public void train(int k) throws NumberFormatException, IOException {
		for (RawEdge edge : rawRequestsByK[k].edges.values()) {
			super.addEdge(edge.key.fromRoutineHash, edge.key.fromIndex, edge.key.toRoutineHash,
					0/* to_index not used */, edge.isAdmin ? 10 : 0, edge.routineCatalog);
		}
	}

	public ByteBuffer getDelta(int k) {
		Map<RawEdge.EndpointKey, RawEdge> newEdges = new HashMap<RawEdge.EndpointKey, RawEdge>();
		for (RawEdge rawEdge : rawRequestsByK[k].edges.values()) {
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
