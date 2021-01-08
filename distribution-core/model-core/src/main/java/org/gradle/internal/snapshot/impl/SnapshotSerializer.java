/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.ValueSnapshot;

public class SnapshotSerializer extends AbstractSerializer<ValueSnapshot> {
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
    private static final int MANAGED_SNAPSHOT = 15;
    private static final int IMMUTABLE_MANAGED_SNAPSHOT = 16;
    private static final int IMPLEMENTATION_SNAPSHOT = 17;
    private static final int DEFAULT_SNAPSHOT = 18;

    private final HashCodeSerializer serializer = new HashCodeSerializer();
    private final Serializer<ImplementationSnapshot> implementationSnapshotSerializer = new ImplementationSnapshotSerializer();

    @Override
    public ValueSnapshot read(Decoder decoder) throws Exception {
        int type = decoder.readSmallInt();
        int size;
        String className;
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
                ImmutableList<ValueSnapshot> arrayElements = readList(decoder, size);
                return new ArrayValueSnapshot(arrayElements);
            case EMPTY_LIST_SNAPSHOT:
                return ListValueSnapshot.EMPTY;
            case LIST_SNAPSHOT:
                size = decoder.readSmallInt();
                ImmutableList<ValueSnapshot> listElements = readList(decoder, size);
                return new ListValueSnapshot(listElements);
            case SET_SNAPSHOT:
                size = decoder.readSmallInt();
                ImmutableSet.Builder<ValueSnapshot> setBuilder = ImmutableSet.builder();
                for (int i = 0; i < size; i++) {
                    setBuilder.add(read(decoder));
                }
                return new SetValueSnapshot(setBuilder.build());
            case MAP_SNAPSHOT:
                size = decoder.readSmallInt();
                ImmutableList.Builder<MapEntrySnapshot<ValueSnapshot>> mapBuilder = ImmutableList.builderWithExpectedSize(size);
                for (int i = 0; i < size; i++) {
                    mapBuilder.add(new MapEntrySnapshot<ValueSnapshot>(read(decoder), read(decoder)));
                }
                return new MapValueSnapshot(mapBuilder.build());
            case MANAGED_SNAPSHOT:
                className = decoder.readString();
                ValueSnapshot state = read(decoder);
                return new ManagedValueSnapshot(className, state);
            case IMMUTABLE_MANAGED_SNAPSHOT:
                className = decoder.readString();
                String value = decoder.readString();
                return new ImmutableManagedValueSnapshot(className, value);
            case IMPLEMENTATION_SNAPSHOT:
                return implementationSnapshotSerializer.read(decoder);
            case DEFAULT_SNAPSHOT:
                return new SerializedValueSnapshot(decoder.readBoolean() ? serializer.read(decoder) : null, decoder.readBinary());
            default:
                throw new IllegalArgumentException("Don't know how to deserialize a snapshot with type tag " + type);
        }
    }

    private ImmutableList<ValueSnapshot> readList(Decoder decoder, int size) throws Exception {
        ImmutableList.Builder<ValueSnapshot> arrayElements = ImmutableList.builderWithExpectedSize(size);
        for (int i = 0; i < size; i++) {
            arrayElements.add(read(decoder));
        }
        return arrayElements.build();
    }

    @Override
    public void write(Encoder encoder, ValueSnapshot snapshot) throws Exception {
        if (snapshot == NullValueSnapshot.INSTANCE) {
            encoder.writeSmallInt(NULL_SNAPSHOT);
        } else if (snapshot instanceof StringValueSnapshot) {
            StringValueSnapshot stringSnapshot = (StringValueSnapshot) snapshot;
            encoder.writeSmallInt(STRING_SNAPSHOT);
            encoder.writeString(stringSnapshot.getValue());
        } else if (snapshot instanceof ListValueSnapshot) {
            ListValueSnapshot listSnapshot = (ListValueSnapshot) snapshot;
            if (listSnapshot.getElements().isEmpty()) {
                encoder.writeSmallInt(EMPTY_LIST_SNAPSHOT);
            } else {
                encoder.writeSmallInt(LIST_SNAPSHOT);
                encoder.writeSmallInt(listSnapshot.getElements().size());
                for (ValueSnapshot valueSnapshot : listSnapshot.getElements()) {
                    write(encoder, valueSnapshot);
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
                write(encoder, valueSnapshot);
            }
        } else if (snapshot instanceof ImplementationSnapshot) {
            ImplementationSnapshot implementationSnapshot = (ImplementationSnapshot) snapshot;
            encoder.writeSmallInt(IMPLEMENTATION_SNAPSHOT);
            implementationSnapshotSerializer.write(encoder, implementationSnapshot);
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
            for (MapEntrySnapshot<ValueSnapshot> entry : mapSnapshot.getEntries()) {
                write(encoder, entry.getKey());
                write(encoder, entry.getValue());
            }
        } else if (snapshot instanceof ArrayValueSnapshot) {
            ArrayValueSnapshot arraySnapshot = (ArrayValueSnapshot) snapshot;
            if (arraySnapshot.getElements().isEmpty()) {
                encoder.writeSmallInt(EMPTY_ARRAY_SNAPSHOT);
            } else {
                encoder.writeSmallInt(ARRAY_SNAPSHOT);
                encoder.writeSmallInt(arraySnapshot.getElements().size());
                for (ValueSnapshot valueSnapshot : arraySnapshot.getElements()) {
                    write(encoder, valueSnapshot);
                }
            }
        } else if (snapshot instanceof ImmutableManagedValueSnapshot) {
            encoder.writeSmallInt(IMMUTABLE_MANAGED_SNAPSHOT);
            ImmutableManagedValueSnapshot valueSnapshot = (ImmutableManagedValueSnapshot) snapshot;
            encoder.writeString(valueSnapshot.getClassName());
            encoder.writeString(valueSnapshot.getValue());
        } else if (snapshot instanceof ManagedValueSnapshot) {
            encoder.writeSmallInt(MANAGED_SNAPSHOT);
            ManagedValueSnapshot managedTypeSnapshot = (ManagedValueSnapshot) snapshot;
            encoder.writeString(managedTypeSnapshot.getClassName());
            write(encoder, managedTypeSnapshot.getState());
        } else {
            throw new IllegalArgumentException("Don't know how to serialize a value of type " + snapshot.getClass().getSimpleName());
        }
    }
}
