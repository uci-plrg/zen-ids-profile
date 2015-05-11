package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;

import edu.uci.eecs.scriptsafe.analysis.request.RequestEdgeSummary;

class FeatureRoleCounts implements FeatureResponseGenerator.Field {

	private int adminCount = 0;
	private int anonymousCount = 0;

	void addCounts(RequestEdgeSummary edge) {
		if (edge != null) {
			adminCount += edge.getAdminCount();
			anonymousCount += edge.getAnonymousCount();
		}
	}

	@Override
	public int getByteCount() {
		return 8;
	}

	@Override
	public void write(ByteBuffer buffer) {
		buffer.putInt(adminCount);
		buffer.putInt(anonymousCount);
	}

	@Override
	public void reset() {
		adminCount = anonymousCount = 0;
	}
}
