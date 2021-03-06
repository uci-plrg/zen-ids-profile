package edu.uci.plrg.cfi.php.feature;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

class FeatureResponseGenerator {

	interface Field {
		int getByteCount();

		void write(ByteBuffer buffer);
		
		void reset();
	}

	private final List<Field> fields = new ArrayList<Field>();

	void addField(Field field) {
		fields.add(field);
	}
	
	void resetAllFields() {
		for (Field field : fields)
			field.reset();
	}

	ByteBuffer generateResponse() {
		int count = 1; // response code
		for (Field field : fields)
			count += field.getByteCount();
		ByteBuffer buffer = ByteBuffer.allocate(count);
		buffer.put(FeatureResponse.OK.code);
		for (Field field : fields)
			field.write(buffer);
		return buffer;
	}
}
