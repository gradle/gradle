/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.util.Map;

class InputPropertiesSerializer implements Serializer<ImmutableMap<String, ValueSnapshot>> {
    private static final int NULL_SNAPSHOT = 0;
    private static final int TRUE_SNAPSHOT = 1;
    private static final int FALSE_SNAPSHOT = 2;
    private static final int STRING_SNAPSHOT = 3;
    private static final int INTEGER_SNAPSHOT = 4;
    private static final int EMPTY_LIST_SNAPSHOT = 5;
    private static final int LIST_SNAPSHOT = 6;
    private static final int SET_SNAPSHOT = 7;
    private static final int DEFAULT_SNAPSHOT = 8;
    private static final ValueSnapshot[] NO_SNAPSHOTS = new ValueSnapshot[0];
    private final HashCodeSerializer serializer = new HashCodeSerializer();

    public ImmutableMap<String, ValueSnapshot> read(Decoder decoder) throws Exception {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return ImmutableMap.of();
        }
        if (size == 1) {
            return ImmutableMap.of(decoder.readString(), readSnapshot(decoder));
        }

        ImmutableMap.Builder<String, ValueSnapshot> builder = ImmutableMap.builder();
        for (int i = 0; i < size; i++) {
            builder.put(decoder.readString(), readSnapshot(decoder));
        }
        return builder.build();
    }

    private ValueSnapshot readSnapshot(Decoder decoder) throws Exception {
        int type = decoder.readSmallInt();
        int size;
        switch (type) {
            case NULL_SNAPSHOT:
                return NullValueSnapshot.INSTANCE;
            case TRUE_SNAPSHOT:
                return BooleanValueSnapshot.TRUE;
            case FALSE_SNAPSHOT:
                return BooleanValueSnapshot.FALSE;
            case STRING_SNAPSHOT:
                return new StringValueSnapshot(decoder.readString());
            case INTEGER_SNAPSHOT:
                return new IntegerValueSnapshot(decoder.readInt());
            case EMPTY_LIST_SNAPSHOT:
                return new ListValueSnapshot(NO_SNAPSHOTS);
            case LIST_SNAPSHOT:
                size = decoder.readSmallInt();
                ValueSnapshot[] elements = new ValueSnapshot[size];
                for (int i = 0; i < size; i++) {
                    elements[i] = readSnapshot(decoder);
                }
                return new ListValueSnapshot(elements);
            case SET_SNAPSHOT:
                size = decoder.readSmallInt();
                ImmutableSet.Builder<ValueSnapshot> builder = ImmutableSet.builder();
                for (int i = 0; i < size; i++) {
                    builder.add(readSnapshot(decoder));
                }
                return new SetValueSnapshot(builder.build());
            case DEFAULT_SNAPSHOT:
                return new SerializedValueSnapshot(decoder.readBoolean() ? serializer.read(decoder) : null, decoder.readBinary());
            default:
                throw new IllegalArgumentException("Don't know how to deserialize a snapshot with type tag " + type);
        }
    }

    public void write(Encoder encoder, ImmutableMap<String, ValueSnapshot> properties) throws Exception {
        encoder.writeSmallInt(properties.size());
        for (Map.Entry<String, ValueSnapshot> entry : properties.entrySet()) {
            encoder.writeString(entry.getKey());
            writeEntry(encoder, entry.getValue());
        }
    }

    private void writeEntry(Encoder encoder, ValueSnapshot snapshot) throws IOException {
        if (snapshot == NullValueSnapshot.INSTANCE) {
            encoder.writeSmallInt(NULL_SNAPSHOT);
        } else if (snapshot instanceof StringValueSnapshot) {
            StringValueSnapshot stringSnapshot = (StringValueSnapshot) snapshot;
            encoder.writeSmallInt(STRING_SNAPSHOT);
            encoder.writeString(stringSnapshot.getValue());
        } else if (snapshot instanceof ListValueSnapshot) {
            ListValueSnapshot listSnapshot = (ListValueSnapshot) snapshot;
            if (listSnapshot.getElements().length == 0) {
                encoder.writeSmallInt(EMPTY_LIST_SNAPSHOT);
            } else {
                encoder.writeSmallInt(LIST_SNAPSHOT);
                encoder.writeSmallInt(listSnapshot.getElements().length);
                for (ValueSnapshot valueSnapshot : listSnapshot.getElements()) {
                    writeEntry(encoder, valueSnapshot);
                }
            }
        } else if (snapshot == BooleanValueSnapshot.TRUE) {
            encoder.writeSmallInt(TRUE_SNAPSHOT);
        } else if (snapshot == BooleanValueSnapshot.FALSE) {
            encoder.writeSmallInt(FALSE_SNAPSHOT);
        } else if (snapshot instanceof IntegerValueSnapshot) {
            IntegerValueSnapshot integerSnapshot = (IntegerValueSnapshot) snapshot;
            encoder.writeSmallInt(INTEGER_SNAPSHOT);
            encoder.writeInt(integerSnapshot.getValue());
        } else if (snapshot instanceof SetValueSnapshot) {
            SetValueSnapshot setSnapshot = (SetValueSnapshot) snapshot;
            encoder.writeSmallInt(SET_SNAPSHOT);
            encoder.writeSmallInt(setSnapshot.getElements().size());
            for (ValueSnapshot valueSnapshot : setSnapshot.getElements()) {
                writeEntry(encoder, valueSnapshot);
            }
        } else if (snapshot instanceof SerializedValueSnapshot) {
            SerializedValueSnapshot valueSnapshot = (SerializedValueSnapshot) snapshot;
            encoder.writeSmallInt(DEFAULT_SNAPSHOT);
            if (valueSnapshot.getImplementationHash() == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                serializer.write(encoder, valueSnapshot.getImplementationHash());
            }
            encoder.writeBinary(valueSnapshot.getValue());
        } else {
            throw new IllegalArgumentException("Don't know how to serialize a value of type " + snapshot.getClass().getSimpleName());
        }
    }
}
