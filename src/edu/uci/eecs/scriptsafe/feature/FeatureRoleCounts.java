package edu.uci.eecs.scriptsafe.feature;

import java.nio.ByteBuffer;

class FeatureRoleCounts implements FeatureResponseGenerator.Field {

	private int adminCount = 0;
	private int anonymousCount = 0;

	void addCounts(FeatureRoleCountElement edge) {
		if (edge != null) {
			adminCount += edge.getAdminCount();
			anonymousCount += edge.getAnonymousCount();
		}
	}
	
	void increment(boolean isAdmin) {
		if (isAdmin)
			adminCount++;
		else
			anonymousCount++;
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
