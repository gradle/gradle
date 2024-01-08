/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.workers.internal;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.attributes.Attribute;
import org.gradle.internal.Cast;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.snapshot.impl.AbstractIsolatedMap;
import org.gradle.internal.snapshot.impl.AttributeDefinitionSnapshot;
import org.gradle.internal.snapshot.impl.BooleanValueSnapshot;
import org.gradle.internal.snapshot.impl.FileValueSnapshot;
import org.gradle.internal.snapshot.impl.IntegerValueSnapshot;
import org.gradle.internal.snapshot.impl.IsolatedArray;
import org.gradle.internal.snapshot.impl.IsolatedArrayOfPrimitive;
import org.gradle.internal.snapshot.impl.IsolatedEnumValueSnapshot;
import org.gradle.internal.snapshot.impl.IsolatedImmutableManagedValue;
import org.gradle.internal.snapshot.impl.IsolatedJavaSerializedValueSnapshot;
import org.gradle.internal.snapshot.impl.IsolatedList;
import org.gradle.internal.snapshot.impl.IsolatedManagedValue;
import org.gradle.internal.snapshot.impl.IsolatedMap;
import org.gradle.internal.snapshot.impl.IsolatedProperties;
import org.gradle.internal.snapshot.impl.IsolatedSet;
import org.gradle.internal.snapshot.impl.LongValueSnapshot;
import org.gradle.internal.snapshot.impl.MapEntrySnapshot;
import org.gradle.internal.snapshot.impl.NullValueSnapshot;
import org.gradle.internal.snapshot.impl.ShortValueSnapshot;
import org.gradle.internal.snapshot.impl.StringValueSnapshot;
import org.gradle.internal.state.Managed;
import org.gradle.internal.state.ManagedFactory;
import org.gradle.internal.state.ManagedFactoryRegistry;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;

import static org.gradle.internal.classloader.ClassLoaderUtils.classFromContextLoader;

public class IsolatableSerializerRegistry extends DefaultSerializerRegistry {
    private static final byte STRING_VALUE = (byte) 0;
    private static final byte BOOLEAN_VALUE = (byte) 1;
    private static final byte SHORT_VALUE = (byte) 2;
    private static final byte INTEGER_VALUE = (byte) 3;
    private static final byte LONG_VALUE = (byte) 4;
    private static final byte ATTRIBUTE_VALUE = (byte) 5;
    private static final byte MANAGED_VALUE = (byte) 6;
    private static final byte IMMUTABLE_MANAGED_VALUE = (byte) 7;
    private static final byte FILE_VALUE = (byte) 8;
    private static final byte NULL_VALUE = (byte) 9;
    private static final byte SERIALIZED_VALUE = (byte) 10;
    private static final byte ENUM_VALUE = (byte) 11;
    private static final byte ISOLATED_MAP = (byte) 12;
    private static final byte ISOLATED_ARRAY = (byte) 13;
    private static final byte ISOLATED_LIST = (byte) 14;
    private static final byte ISOLATED_SET = (byte) 15;
    private static final byte ISOLATED_PROPERTIES = (byte) 16;
    private static final byte ISOLATED_ARRAY_OF_PRIMITIVE = (byte) 17;

    private static final byte ISOLATABLE_TYPE = (byte) 0;
    private static final byte ARRAY_TYPE = (byte) 1;
    private static final byte OTHER_TYPE = (byte) 2;
    private static final byte NULL_TYPE = (byte) 3;

    private final IsolatableSerializer<?>[] isolatableSerializers = {
        new StringValueSnapshotSerializer(),
        new BooleanValueSnapshotSerializer(),
        new ShortValueSnapshotSerializer(),
        new IntegerValueSnapshotSerializer(),
        new LongValueSnapshotSerializer(),
        new AttributeDefinitionSnapshotSerializer(),
        new IsolatedManagedValueSerializer(),
        new IsolatedImmutableManagedValueSerializer(),
        new FileValueSnapshotSerializer(),
        new NullValueSnapshotSerializer(),
        new IsolatedJavaSerializedValueSnapshotSerializer(),
        new IsolatedEnumValueSnapshotSerializer(),
        new IsolatedMapSerializer(),
        new IsolatedArraySerializer(),
        new IsolatedListSerializer(),
        new IsolatedSetSerializer(),
        new IsolatedPropertiesSerializer(),
        new IsolatedPrimitiveArraySerializer()
    };

    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ManagedFactoryRegistry managedFactoryRegistry;

    public IsolatableSerializerRegistry(ClassLoaderHierarchyHasher classLoaderHierarchyHasher, ManagedFactoryRegistry managedFactoryRegistry) {
        super(false);
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.managedFactoryRegistry = managedFactoryRegistry;
        for (IsolatableSerializer<?> serializer : isolatableSerializers) {
            register(serializer.getIsolatableClass(), Cast.uncheckedCast(serializer));
        }
    }

    public static IsolatableSerializerRegistry create(ClassLoaderHierarchyHasher classLoaderHierarchyHasher, ManagedFactoryRegistry managedFactoryRegistry) {
        return new IsolatableSerializerRegistry(classLoaderHierarchyHasher, managedFactoryRegistry);
    }

    public Isolatable<?> readIsolatable(Decoder decoder) throws Exception {
        byte type = decoder.readByte();
        Class<? extends Isolatable<?>> isolatableClass = isolatableSerializers[type].getIsolatableClass();
        return build(isolatableClass).read(decoder);
    }

    public void writeIsolatable(Encoder encoder, Isolatable<?> isolatable) throws Exception {
        build(isolatable.getClass()).write(encoder, Cast.uncheckedCast(isolatable));
    }

    private void readIsolatableSequence(Decoder decoder, ImmutableCollection.Builder<Isolatable<?>> builder) throws Exception {
        int size = decoder.readInt();
        for (int i = 0; i < size; i++) {
            builder.add(readIsolatable(decoder));
        }
    }

    private void writeIsolatableSequence(Encoder encoder, Collection<Isolatable<?>> elements) throws Exception {
        encoder.writeInt(elements.size());
        for (Isolatable<?> isolatable : elements) {
            writeIsolatable(encoder, isolatable);
        }
    }

    private Object readState(Decoder decoder) throws Exception {
        byte stateType = decoder.readByte();
        if (stateType == NULL_TYPE) {
            return null;
        } else if (stateType == ISOLATABLE_TYPE) {
            return readIsolatable(decoder);
        } else if (stateType == ARRAY_TYPE) {
            return readArray(decoder);
        } else {
            String stateClassName = decoder.readString();
            Class<?> stateClass = fromClassName(stateClassName);
            useJavaSerialization(stateClass);
            return build(stateClass).read(decoder);
        }
    }

    private void writeState(Encoder encoder, Object state) throws Exception {
        if (state == null) {
            encoder.writeByte(NULL_TYPE);
        } else if (state instanceof Isolatable) {
            encoder.writeByte(ISOLATABLE_TYPE);
            writeIsolatable(encoder, (Isolatable<?>) state);
        } else if (state.getClass().isArray()) {
            encoder.writeByte(ARRAY_TYPE);
            writeArray(encoder, state);
        } else {
            encoder.writeByte(OTHER_TYPE);
            encoder.writeString(state.getClass().getName());
            useJavaSerialization(state.getClass());
            build(state.getClass()).write(encoder, Cast.uncheckedCast(state));
        }
    }

    private Object readArray(Decoder decoder) throws Exception {
        String componentTypeName = decoder.readString();
        Class<?> componentType = fromClassName(componentTypeName);
        int length = decoder.readInt();
        Object state = Array.newInstance(componentType, length);
        for (int i = 0; i < length; i++) {
            Array.set(state, i, readState(decoder));
        }
        return state;
    }

    private void writeArray(Encoder encoder, Object state) throws Exception {
        Class<?> componentType = state.getClass().getComponentType();
        if (componentType.isPrimitive()) {
            throw new IllegalArgumentException("Unsupported state array type: " + state.getClass());
        }
        encoder.writeString(componentType.getName());
        Object[] array = (Object[]) state;
        encoder.writeInt(array.length);
        for (Object o : array) {
            writeState(encoder, o);
        }
    }

    private interface IsolatableSerializer<T extends Isolatable<?>> extends Serializer<T> {
        Class<T> getIsolatableClass();
    }

    private static Class<?> fromClassName(String className) {
        return classFromContextLoader(className);
    }

    private static class StringValueSnapshotSerializer implements IsolatableSerializer<StringValueSnapshot> {
        @Override
        public void write(Encoder encoder, StringValueSnapshot value) throws Exception {
            encoder.writeByte(STRING_VALUE);
            encoder.writeString(value.getValue());
        }

        @Override
        public StringValueSnapshot read(Decoder decoder) throws Exception {
            return new StringValueSnapshot(decoder.readString());
        }

        @Override
        public Class<StringValueSnapshot> getIsolatableClass() {
            return StringValueSnapshot.class;
        }
    }

    private static class BooleanValueSnapshotSerializer implements IsolatableSerializer<BooleanValueSnapshot> {
        @Override
        public void write(Encoder encoder, BooleanValueSnapshot value) throws Exception {
            encoder.writeByte(BOOLEAN_VALUE);
            encoder.writeBoolean(value.getValue());
        }

        @Override
        public BooleanValueSnapshot read(Decoder decoder) throws Exception {
            return new BooleanValueSnapshot(decoder.readBoolean());
        }

        @Override
        public Class<BooleanValueSnapshot> getIsolatableClass() {
            return BooleanValueSnapshot.class;
        }
    }

    private static class ShortValueSnapshotSerializer implements IsolatableSerializer<ShortValueSnapshot> {
        @Override
        public void write(Encoder encoder, ShortValueSnapshot value) throws Exception {
            encoder.writeByte(SHORT_VALUE);
            encoder.writeInt(value.getValue());
        }

        @Override
        public ShortValueSnapshot read(Decoder decoder) throws Exception {
            return new ShortValueSnapshot((short) decoder.readInt());
        }

        @Override
        public Class<ShortValueSnapshot> getIsolatableClass() {
            return ShortValueSnapshot.class;
        }
    }

    private static class IntegerValueSnapshotSerializer implements IsolatableSerializer<IntegerValueSnapshot> {
        @Override
        public void write(Encoder encoder, IntegerValueSnapshot value) throws Exception {
            encoder.writeByte(INTEGER_VALUE);
            encoder.writeInt(value.getValue());
        }

        @Override
        public IntegerValueSnapshot read(Decoder decoder) throws Exception {
            return new IntegerValueSnapshot(decoder.readInt());
        }

        @Override
        public Class<IntegerValueSnapshot> getIsolatableClass() {
            return IntegerValueSnapshot.class;
        }
    }

    private static class LongValueSnapshotSerializer implements IsolatableSerializer<LongValueSnapshot> {
        @Override
        public void write(Encoder encoder, LongValueSnapshot value) throws Exception {
            encoder.writeByte(LONG_VALUE);
            encoder.writeLong(value.getValue());
        }

        @Override
        public LongValueSnapshot read(Decoder decoder) throws Exception {
            return new LongValueSnapshot(decoder.readLong());
        }

        @Override
        public Class<LongValueSnapshot> getIsolatableClass() {
            return LongValueSnapshot.class;
        }
    }

    private class AttributeDefinitionSnapshotSerializer implements IsolatableSerializer<AttributeDefinitionSnapshot> {
        @Override
        public void write(Encoder encoder, AttributeDefinitionSnapshot value) throws Exception {
            encoder.writeByte(ATTRIBUTE_VALUE);
            encoder.writeString(value.getValue().getType().getName());
            encoder.writeString(value.getValue().getName());
        }

        @Override
        public AttributeDefinitionSnapshot read(Decoder decoder) throws Exception {
            String className = decoder.readString();
            Class<?> attributeClass = fromClassName(className);
            String name = decoder.readString();
            return new AttributeDefinitionSnapshot(Attribute.of(name, attributeClass), classLoaderHierarchyHasher);
        }

        @Override
        public Class<AttributeDefinitionSnapshot> getIsolatableClass() {
            return AttributeDefinitionSnapshot.class;
        }
    }

    private class IsolatedImmutableManagedValueSerializer implements IsolatableSerializer<IsolatedImmutableManagedValue> {
        @Override
        public void write(Encoder encoder, IsolatedImmutableManagedValue value) throws Exception {
            encoder.writeByte(IMMUTABLE_MANAGED_VALUE);
            encoder.writeInt(value.getValue().getFactoryId());
            encoder.writeString(value.getValue().publicType().getName());
            writeState(encoder, value.getValue().unpackState());
        }

        @Override
        public IsolatedImmutableManagedValue read(Decoder decoder) throws Exception {
            int factoryId = decoder.readInt();
            String publicClassName = decoder.readString();
            Class<?> publicClass = fromClassName(publicClassName);

            ManagedFactory factory = managedFactoryRegistry.lookup(factoryId);
            Managed managed = Cast.uncheckedCast(factory.fromState(publicClass, readState(decoder)));
            assert managed != null;
            return new IsolatedImmutableManagedValue(managed, managedFactoryRegistry);
        }

        @Override
        public Class<IsolatedImmutableManagedValue> getIsolatableClass() {
            return IsolatedImmutableManagedValue.class;
        }
    }

    private class IsolatedManagedValueSerializer implements IsolatableSerializer<IsolatedManagedValue> {
        @Override
        public void write(Encoder encoder, IsolatedManagedValue value) throws Exception {
            encoder.writeByte(MANAGED_VALUE);
            encoder.writeInt(value.getFactoryId());
            encoder.writeString(value.getTargetType().getName());
            Isolatable<?> state = value.getState();
            writeIsolatable(encoder, state);
        }

        @Override
        public IsolatedManagedValue read(Decoder decoder) throws Exception {
            int factoryId = decoder.readInt();
            String publicClassName = decoder.readString();
            Class<?> publicClass = fromClassName(publicClassName);
            Isolatable<?> state = readIsolatable(decoder);

            ManagedFactory factory = managedFactoryRegistry.lookup(factoryId);
            return new IsolatedManagedValue(publicClass, factory, state);
        }

        @Override
        public Class<IsolatedManagedValue> getIsolatableClass() {
            return IsolatedManagedValue.class;
        }
    }

    private static class FileValueSnapshotSerializer implements IsolatableSerializer<FileValueSnapshot> {
        @Override
        public void write(Encoder encoder, FileValueSnapshot value) throws Exception {
            encoder.writeByte(FILE_VALUE);
            encoder.writeString(value.getValue());
        }

        @Override
        public FileValueSnapshot read(Decoder decoder) throws Exception {
            return new FileValueSnapshot(decoder.readString());
        }

        @Override
        public Class<FileValueSnapshot> getIsolatableClass() {
            return FileValueSnapshot.class;
        }
    }

    private static class IsolatedJavaSerializedValueSnapshotSerializer implements IsolatableSerializer<IsolatedJavaSerializedValueSnapshot> {
        @Override
        public void write(Encoder encoder, IsolatedJavaSerializedValueSnapshot value) throws Exception {
            encoder.writeByte(SERIALIZED_VALUE);
            encoder.writeString(value.getOriginalClass().getName());
            HashCode implementationHash = value.getImplementationHash();
            if (implementationHash == null) {
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                encoder.writeBinary(implementationHash.toByteArray());
            }
            encoder.writeBinary(value.getValue());
        }

        @Override
        public IsolatedJavaSerializedValueSnapshot read(Decoder decoder) throws Exception {
            String originalClassName = decoder.readString();
            Class<?> originalClass = fromClassName(originalClassName);
            HashCode implementationHash = null;
            if (decoder.readBoolean()) {
                implementationHash = HashCode.fromBytes(decoder.readBinary());
            }
            byte[] serializedBytes = decoder.readBinary();
            return new IsolatedJavaSerializedValueSnapshot(implementationHash, serializedBytes, originalClass);
        }

        @Override
        public Class<IsolatedJavaSerializedValueSnapshot> getIsolatableClass() {
            return IsolatedJavaSerializedValueSnapshot.class;
        }
    }

    private static class NullValueSnapshotSerializer implements IsolatableSerializer<NullValueSnapshot> {
        @Override
        public void write(Encoder encoder, NullValueSnapshot value) throws Exception {
            encoder.writeByte(NULL_VALUE);
        }

        @Override
        public NullValueSnapshot read(Decoder decoder) {
            return NullValueSnapshot.INSTANCE;
        }

        @Override
        public Class<NullValueSnapshot> getIsolatableClass() {
            return NullValueSnapshot.class;
        }
    }

    public static class IsolatedEnumValueSnapshotSerializer implements IsolatableSerializer<IsolatedEnumValueSnapshot> {
        @Override
        public void write(Encoder encoder, IsolatedEnumValueSnapshot value) throws Exception {
            encoder.writeByte(ENUM_VALUE);
            encoder.writeString(value.getClassName());
            encoder.writeString(value.getName());
        }

        @Override
        public IsolatedEnumValueSnapshot read(Decoder decoder) throws Exception {
            String className = decoder.readString();
            String name = decoder.readString();
            Class<? extends Enum<?>> enumClass = Cast.uncheckedCast(fromClassName(className));
            return new IsolatedEnumValueSnapshot(Enum.valueOf(Cast.uncheckedCast(enumClass), name));
        }

        @Override
        public Class<IsolatedEnumValueSnapshot> getIsolatableClass() {
            return IsolatedEnumValueSnapshot.class;
        }
    }

    private abstract class AbstractIsolatedMapSerializer<T extends AbstractIsolatedMap<?>> implements IsolatableSerializer<T> {
        protected abstract T getIsolatedObject(ImmutableList<MapEntrySnapshot<Isolatable<?>>> entries);

        protected abstract byte getTypeByte();

        @Override
        public void write(Encoder encoder, T value) throws Exception {
            encoder.writeByte(getTypeByte());
            List<MapEntrySnapshot<Isolatable<?>>> entrySnapshots = value.getEntries();
            encoder.writeInt(entrySnapshots.size());
            for (MapEntrySnapshot<Isolatable<?>> entrySnapshot : entrySnapshots) {
                writeIsolatable(encoder, entrySnapshot.getKey());
                writeIsolatable(encoder, entrySnapshot.getValue());
            }
        }

        @Override
        public T read(Decoder decoder) throws Exception {
            int size = decoder.readInt();
            ImmutableList.Builder<MapEntrySnapshot<Isolatable<?>>> builder = ImmutableList.builder();
            for (int i = 0; i < size; i++) {
                Isolatable<?> key = readIsolatable(decoder);
                Isolatable<?> value = readIsolatable(decoder);
                MapEntrySnapshot<Isolatable<?>> entry = new MapEntrySnapshot<>(key, value);
                builder.add(entry);
            }
            return getIsolatedObject(builder.build());
        }
    }

    private class IsolatedMapSerializer extends AbstractIsolatedMapSerializer<IsolatedMap> {
        @Override
        public Class<IsolatedMap> getIsolatableClass() {
            return IsolatedMap.class;
        }

        @Override
        protected IsolatedMap getIsolatedObject(ImmutableList<MapEntrySnapshot<Isolatable<?>>> entries) {
            return new IsolatedMap(entries);
        }

        @Override
        protected byte getTypeByte() {
            return ISOLATED_MAP;
        }
    }

    private class IsolatedPropertiesSerializer extends AbstractIsolatedMapSerializer<IsolatedProperties> {
        @Override
        protected IsolatedProperties getIsolatedObject(ImmutableList<MapEntrySnapshot<Isolatable<?>>> entries) {
            return new IsolatedProperties(entries);
        }

        @Override
        protected byte getTypeByte() {
            return ISOLATED_PROPERTIES;
        }

        @Override
        public Class<IsolatedProperties> getIsolatableClass() {
            return IsolatedProperties.class;
        }
    }

    @NonNullApi
    private static class IsolatedPrimitiveArraySerializer implements IsolatableSerializer<IsolatedArrayOfPrimitive> {
        @Override
        public Class<IsolatedArrayOfPrimitive> getIsolatableClass() {
            return IsolatedArrayOfPrimitive.class;
        }

        @Override
        public IsolatedArrayOfPrimitive read(Decoder decoder) throws Exception {
            byte primitiveTypeCode = decoder.readByte();
            byte[] bytes = readLengthPrefixedByteArray(decoder);
            return IsolatedArrayOfPrimitive.fromByteArray(primitiveTypeCode, bytes);
        }

        @Override
        public void write(Encoder encoder, IsolatedArrayOfPrimitive value) throws Exception {
            encoder.writeByte(ISOLATED_ARRAY_OF_PRIMITIVE);
            encoder.writeByte(value.getPrimitiveTypeCode());
            writeLengthPrefixedByteArray(encoder, value.toByteArray());
        }

        private static byte[] readLengthPrefixedByteArray(Decoder decoder) throws IOException {
            int length = decoder.readInt();
            byte[] bytes = new byte[length];
            decoder.readBytes(bytes);
            return bytes;
        }

        private static void writeLengthPrefixedByteArray(Encoder encoder, byte[] bytes) throws IOException {
            encoder.writeInt(bytes.length);
            encoder.writeBytes(bytes);
        }
    }

    private class IsolatedArraySerializer implements IsolatableSerializer<IsolatedArray> {
        @Override
        public void write(Encoder encoder, IsolatedArray value) throws Exception {
            encoder.writeByte(ISOLATED_ARRAY);
            encoder.writeString(value.getArrayType().getName());
            writeIsolatableSequence(encoder, value.getElements());
        }

        @Override
        public IsolatedArray read(Decoder decoder) throws Exception {
            ImmutableList.Builder<Isolatable<?>> builder = ImmutableList.builder();
            Class<?> arrayType = fromClassName(decoder.readString());
            readIsolatableSequence(decoder, builder);
            return new IsolatedArray(builder.build(), arrayType);
        }

        @Override
        public Class<IsolatedArray> getIsolatableClass() {
            return IsolatedArray.class;
        }
    }

    private class IsolatedListSerializer implements IsolatableSerializer<IsolatedList> {
        @Override
        public void write(Encoder encoder, IsolatedList value) throws Exception {
            encoder.writeByte(ISOLATED_LIST);
            writeIsolatableSequence(encoder, value.getElements());
        }

        @Override
        public IsolatedList read(Decoder decoder) throws Exception {
            ImmutableList.Builder<Isolatable<?>> builder = ImmutableList.builder();
            readIsolatableSequence(decoder, builder);
            return new IsolatedList(builder.build());
        }

        @Override
        public Class<IsolatedList> getIsolatableClass() {
            return IsolatedList.class;
        }
    }

    private class IsolatedSetSerializer implements IsolatableSerializer<IsolatedSet> {
        @Override
        public void write(Encoder encoder, IsolatedSet value) throws Exception {
            encoder.writeByte(ISOLATED_SET);
            writeIsolatableSequence(encoder, value.getElements());
        }

        @Override
        public IsolatedSet read(Decoder decoder) throws Exception {
            ImmutableSet.Builder<Isolatable<?>> builder = ImmutableSet.builder();
            readIsolatableSequence(decoder, builder);
            return new IsolatedSet(builder.build());
        }

        @Override
        public Class<IsolatedSet> getIsolatableClass() {
            return IsolatedSet.class;
        }
    }
}
