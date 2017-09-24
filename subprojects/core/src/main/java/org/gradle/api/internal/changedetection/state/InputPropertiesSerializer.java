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
import com.google.common.collect.ImmutableSortedMap;
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
    private static final int LONG_SNAPSHOT = 5;
    private static final int SHORT_SNAPSHOT = 6;
    private static final int FILE_SNAPSHOT = 7;
    private static final int ENUM_SNAPSHOT = 8;
    private static final int EMPTY_ARRAY_SNAPSHOT = 9;
    private static final int ARRAY_SNAPSHOT = 10;
    private static final int EMPTY_LIST_SNAPSHOT = 11;
    private static final int LIST_SNAPSHOT = 12;
    private static final int SET_SNAPSHOT = 13;
    private static final int MAP_SNAPSHOT = 14;
    private static final int PROVIDER_SNAPSHOT = 15;
    private static final int MANAGED_NAMED_SNAPSHOT = 16;
    private static final int DEFAULT_SNAPSHOT = 17;

    private final HashCodeSerializer serializer = new HashCodeSerializer();

    public ImmutableSortedMap<String, ValueSnapshot> read(Decoder decoder) throws Exception {
        int size = decoder.readSmallInt();
        if (size == 0) {
            return ImmutableSortedMap.of();
        }
        if (size == 1) {
            return ImmutableSortedMap.of(decoder.readString(), readSnapshot(decoder));
        }

        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (int i = 0; i < size; i++) {
            builder.put(decoder.readString(), readSnapshot(decoder));
        }
        return builder.build();
    }

    private ValueSnapshot readSnapshot(Decoder decoder) throws Exception {
        int type = decoder.readSmallInt();
        int size;
        ValueSnapshot[] elements;
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
            case LONG_SNAPSHOT:
                return new LongValueSnapshot(decoder.readLong());
            case SHORT_SNAPSHOT:
                return new ShortValueSnapshot((short) decoder.readInt());
            case FILE_SNAPSHOT:
                return new FileValueSnapshot(decoder.readString());
            case ENUM_SNAPSHOT:
                return new EnumValueSnapshot(decoder.readString(), decoder.readString());
            case EMPTY_ARRAY_SNAPSHOT:
                return ArrayValueSnapshot.EMPTY;
            case ARRAY_SNAPSHOT:
                size = decoder.readSmallInt();
                elements = new ValueSnapshot[size];
                for (int i = 0; i < size; i++) {
                    elements[i] = readSnapshot(decoder);
                }
                return new ArrayValueSnapshot(elements);
            case EMPTY_LIST_SNAPSHOT:
                return ListValueSnapshot.EMPTY;
            case LIST_SNAPSHOT:
                size = decoder.readSmallInt();
                elements = new ValueSnapshot[size];
                for (int i = 0; i < size; i++) {
                    elements[i] = readSnapshot(decoder);
                }
                return new ListValueSnapshot(elements);
            case SET_SNAPSHOT:
                size = decoder.readSmallInt();
                ImmutableSet.Builder<ValueSnapshot> setBuilder = ImmutableSet.builder();
                for (int i = 0; i < size; i++) {
                    setBuilder.add(readSnapshot(decoder));
                }
                return new SetValueSnapshot(setBuilder.build());
            case MAP_SNAPSHOT:
                size = decoder.readSmallInt();
                ImmutableMap.Builder<ValueSnapshot, ValueSnapshot> mapBuilder = ImmutableMap.builder();
                for (int i = 0; i < size; i++) {
                    mapBuilder.put(readSnapshot(decoder), readSnapshot(decoder));
                }
                return new MapValueSnapshot(mapBuilder.build());
            case PROVIDER_SNAPSHOT:
                return new ProviderSnapshot(readSnapshot(decoder));
            case MANAGED_NAMED_SNAPSHOT:
                return new ManagedNamedTypeSnapshot(decoder.readString(), decoder.readString());
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
        } else if (snapshot instanceof LongValueSnapshot) {
            LongValueSnapshot longSnapshot = (LongValueSnapshot) snapshot;
            encoder.writeSmallInt(LONG_SNAPSHOT);
            encoder.writeLong(longSnapshot.getValue());
        } else if (snapshot instanceof ShortValueSnapshot) {
            ShortValueSnapshot shortSnapshot = (ShortValueSnapshot) snapshot;
            encoder.writeSmallInt(SHORT_SNAPSHOT);
            encoder.writeInt(shortSnapshot.getValue());
        } else if (snapshot instanceof FileValueSnapshot) {
            FileValueSnapshot fileSnapshot = (FileValueSnapshot) snapshot;
            encoder.writeSmallInt(FILE_SNAPSHOT);
            encoder.writeString(fileSnapshot.getValue());
        } else if (snapshot instanceof EnumValueSnapshot) {
            EnumValueSnapshot enumSnapshot = (EnumValueSnapshot) snapshot;
            encoder.writeSmallInt(ENUM_SNAPSHOT);
            encoder.writeString(enumSnapshot.getClassName());
            encoder.writeString(enumSnapshot.getName());
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
        } else if (snapshot instanceof MapValueSnapshot) {
            MapValueSnapshot mapSnapshot = (MapValueSnapshot) snapshot;
            encoder.writeSmallInt(MAP_SNAPSHOT);
            encoder.writeSmallInt(mapSnapshot.getEntries().size());
            for (Map.Entry<ValueSnapshot, ValueSnapshot> entry : mapSnapshot.getEntries().entrySet()) {
                writeEntry(encoder, entry.getKey());
                writeEntry(encoder, entry.getValue());
            }
        } else if (snapshot instanceof ArrayValueSnapshot) {
            ArrayValueSnapshot arraySnapshot = (ArrayValueSnapshot) snapshot;
            if (arraySnapshot.getElements().length == 0) {
                encoder.writeSmallInt(EMPTY_ARRAY_SNAPSHOT);
            } else {
                encoder.writeSmallInt(ARRAY_SNAPSHOT);
                encoder.writeSmallInt(arraySnapshot.getElements().length);
                for (ValueSnapshot valueSnapshot : arraySnapshot.getElements()) {
                    writeEntry(encoder, valueSnapshot);
                }
            }
        } else if (snapshot instanceof ProviderSnapshot) {
            encoder.writeSmallInt(PROVIDER_SNAPSHOT);
            ProviderSnapshot providerSnapshot = (ProviderSnapshot) snapshot;
            writeEntry(encoder, providerSnapshot.getValue());
        } else if (snapshot instanceof ManagedNamedTypeSnapshot) {
            encoder.writeSmallInt(MANAGED_NAMED_SNAPSHOT);
            ManagedNamedTypeSnapshot namedSnapshot = (ManagedNamedTypeSnapshot) snapshot;
            encoder.writeString(namedSnapshot.getClassName());
            encoder.writeString(namedSnapshot.getName());
        } else {
            throw new IllegalArgumentException("Don't know how to serialize a value of type " + snapshot.getClass().getSimpleName());
        }
    }
}
