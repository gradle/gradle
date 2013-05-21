package org.gradle.api.internal.changedetection.state;

import org.gradle.messaging.serialize.DataStreamBackedSerializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
* By Szczepan Faber on 5/21/13
*/
class LongSerializer extends DataStreamBackedSerializer<Long> {
    @Override
    public Long read(DataInput dataInput) throws Exception {
        return dataInput.readLong();
    }

    @Override
    public void write(DataOutput dataOutput, Long value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("This serializer does not serialize null values.");
        }
        dataOutput.writeLong(value);
    }
}
