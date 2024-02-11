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
import org.gradle.internal.snapshot.impl.ArrayOfPrimitiveValueSnapshot;
import org.gradle.internal.snapshot.impl.AttributeDefinitionSnapshot;
import org.gradle.internal.snapshot.impl.BooleanValueSnapshot;
import org.gradle.internal.snapshot.impl.FileValueSnapshot;
import org.gradle.internal.snapshot.impl.IntegerValueSnapshot;
import org.gradle.internal.snapshot.impl.IsolatedArray;
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
        new IsolatedArrayOfPrimitiveSerializer()
    };

    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ManagedFactoryRegistry managedFactoryRegistry;

    public IsolatableSerializerRegistry(ClassLoaderHierarchyHasher classLoaderHierarchyHasher, ManagedFactoryRegistry managedFactoryRegistry) {
        super(false);
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.managedFactoryRegistry = managedFactoryRegistry;
        registerIsolatableSerializers();
    }

    private void registerIsolatableSerializers() {
        for (int i = 0; i < isolatableSerializers.length; i++) {
            IsolatableSerializer<?> serializer = isolatableSerializers[i];
            assert serializer.getSerializerIndex() == i;
            register(serializer.getIsolatableClass(), Cast.uncheckedCast(serializer));
        }
    }

    public static IsolatableSerializerRegistry create(ClassLoaderHierarchyHasher classLoaderHierarchyHasher, ManagedFactoryRegistry managedFactoryRegistry) {
        return new IsolatableSerializerRegistry(classLoaderHierarchyHasher, managedFactoryRegistry);
    }

    public Isolatable<?> readIsolatable(Decoder decoder) throws Exception {
        byte serializerIndex = decoder.readByte();
        Class<? extends Isolatable<?>> isolatableClass = isolatableSerializers[serializerIndex].getIsolatableClass();
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

    private static abstract class IsolatableSerializer<T extends Isolatable<?>> implements Serializer<T> {

        public abstract Class<T> getIsolatableClass();

        /**
         * Index of this serializer in the {@link IsolatableSerializerRegistry#isolatableSerializers list of serializers}.
         */
        public abstract byte getSerializerIndex();

        protected abstract void serialize(Encoder encoder, T value) throws Exception;

        protected abstract T deserialize(Decoder decoder) throws Exception;

        @Override
        public final void write(Encoder encoder, T value) throws Exception {
            encoder.writeByte(getSerializerIndex());
            serialize(encoder, value);
        }

        @Override
        public final T read(Decoder decoder) throws Exception {
            // just here for symmetry in the subclasses
            return deserialize(decoder);
        }
    }

    private static Class<?> fromClassName(String className) {
        return classFromContextLoader(className);
    }

    private static class StringValueSnapshotSerializer extends IsolatableSerializer<StringValueSnapshot> {

        @Override
        protected void serialize(Encoder encoder, StringValueSnapshot value) throws Exception {
            encoder.writeString(value.getValue());
        }

        @Override
        protected StringValueSnapshot deserialize(Decoder decoder) throws Exception {
            return new StringValueSnapshot(decoder.readString());
        }

        @Override
        public Class<StringValueSnapshot> getIsolatableClass() {
            return StringValueSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return STRING_VALUE;
        }
    }

    private static class BooleanValueSnapshotSerializer extends IsolatableSerializer<BooleanValueSnapshot> {
        @Override
        protected void serialize(Encoder encoder, BooleanValueSnapshot value) throws Exception {
            encoder.writeBoolean(value.getValue());
        }

        @Override
        protected BooleanValueSnapshot deserialize(Decoder decoder) throws Exception {
            return new BooleanValueSnapshot(decoder.readBoolean());
        }

        @Override
        public Class<BooleanValueSnapshot> getIsolatableClass() {
            return BooleanValueSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return BOOLEAN_VALUE;
        }
    }

    private static class ShortValueSnapshotSerializer extends IsolatableSerializer<ShortValueSnapshot> {
        @Override
        protected void serialize(Encoder encoder, ShortValueSnapshot value) throws Exception {
            // TODO: consider changing to `encoder.writeShort`
            encoder.writeInt(value.getValue());
        }

        @Override
        protected ShortValueSnapshot deserialize(Decoder decoder) throws Exception {
            // TODO: consider changing to `decoder.readShort`
            return new ShortValueSnapshot((short) decoder.readInt());
        }

        @Override
        public Class<ShortValueSnapshot> getIsolatableClass() {
            return ShortValueSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return SHORT_VALUE;
        }
    }

    private static class IntegerValueSnapshotSerializer extends IsolatableSerializer<IntegerValueSnapshot> {
        @Override
        protected void serialize(Encoder encoder, IntegerValueSnapshot value) throws Exception {
            encoder.writeInt(value.getValue());
        }

        @Override
        protected IntegerValueSnapshot deserialize(Decoder decoder) throws Exception {
            return new IntegerValueSnapshot(decoder.readInt());
        }

        @Override
        public Class<IntegerValueSnapshot> getIsolatableClass() {
            return IntegerValueSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return INTEGER_VALUE;
        }
    }

    private static class LongValueSnapshotSerializer extends IsolatableSerializer<LongValueSnapshot> {
        @Override
        protected void serialize(Encoder encoder, LongValueSnapshot value) throws Exception {
            encoder.writeLong(value.getValue());
        }

        @Override
        protected LongValueSnapshot deserialize(Decoder decoder) throws Exception {
            return new LongValueSnapshot(decoder.readLong());
        }

        @Override
        public Class<LongValueSnapshot> getIsolatableClass() {
            return LongValueSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return LONG_VALUE;
        }
    }

    private class AttributeDefinitionSnapshotSerializer extends IsolatableSerializer<AttributeDefinitionSnapshot> {
        @Override
        protected void serialize(Encoder encoder, AttributeDefinitionSnapshot value) throws Exception {
            encoder.writeString(value.getValue().getType().getName());
            encoder.writeString(value.getValue().getName());
        }

        @Override
        protected AttributeDefinitionSnapshot deserialize(Decoder decoder) throws Exception {
            String className = decoder.readString();
            Class<?> attributeClass = fromClassName(className);
            String name = decoder.readString();
            return new AttributeDefinitionSnapshot(Attribute.of(name, attributeClass), classLoaderHierarchyHasher);
        }

        @Override
        public Class<AttributeDefinitionSnapshot> getIsolatableClass() {
            return AttributeDefinitionSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return ATTRIBUTE_VALUE;
        }
    }

    private class IsolatedImmutableManagedValueSerializer extends IsolatableSerializer<IsolatedImmutableManagedValue> {
        @Override
        protected void serialize(Encoder encoder, IsolatedImmutableManagedValue value) throws Exception {
            encoder.writeInt(value.getValue().getFactoryId());
            encoder.writeString(value.getValue().publicType().getName());
            writeState(encoder, value.getValue().unpackState());
        }

        @Override
        protected IsolatedImmutableManagedValue deserialize(Decoder decoder) throws Exception {
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

        @Override
        public byte getSerializerIndex() {
            return IMMUTABLE_MANAGED_VALUE;
        }
    }

    private class IsolatedManagedValueSerializer extends IsolatableSerializer<IsolatedManagedValue> {
        @Override
        protected void serialize(Encoder encoder, IsolatedManagedValue value) throws Exception {
            encoder.writeInt(value.getFactoryId());
            encoder.writeString(value.getTargetType().getName());
            Isolatable<?> state = value.getState();
            writeIsolatable(encoder, state);
        }

        @Override
        protected IsolatedManagedValue deserialize(Decoder decoder) throws Exception {
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

        @Override
        public byte getSerializerIndex() {
            return MANAGED_VALUE;
        }
    }

    private static class FileValueSnapshotSerializer extends IsolatableSerializer<FileValueSnapshot> {
        @Override
        protected void serialize(Encoder encoder, FileValueSnapshot value) throws Exception {
            encoder.writeString(value.getValue());
        }

        @Override
        protected FileValueSnapshot deserialize(Decoder decoder) throws Exception {
            return new FileValueSnapshot(decoder.readString());
        }

        @Override
        public Class<FileValueSnapshot> getIsolatableClass() {
            return FileValueSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return FILE_VALUE;
        }
    }

    private static class IsolatedJavaSerializedValueSnapshotSerializer extends IsolatableSerializer<IsolatedJavaSerializedValueSnapshot> {

        @Override
        protected void serialize(Encoder encoder, IsolatedJavaSerializedValueSnapshot value) throws Exception {
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
        protected IsolatedJavaSerializedValueSnapshot deserialize(Decoder decoder) throws Exception {
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

        @Override
        public byte getSerializerIndex() {
            return SERIALIZED_VALUE;
        }
    }

    private static class NullValueSnapshotSerializer extends IsolatableSerializer<NullValueSnapshot> {
        @Override
        protected void serialize(Encoder encoder, NullValueSnapshot value) {
        }

        @Override
        protected NullValueSnapshot deserialize(Decoder decoder) {
            return NullValueSnapshot.INSTANCE;
        }

        @Override
        public Class<NullValueSnapshot> getIsolatableClass() {
            return NullValueSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return NULL_VALUE;
        }
    }


    public static class IsolatedEnumValueSnapshotSerializer extends IsolatableSerializer<IsolatedEnumValueSnapshot> {

        @Override
        protected void serialize(Encoder encoder, IsolatedEnumValueSnapshot value) throws Exception {
            encoder.writeString(value.getClassName());
            encoder.writeString(value.getName());
        }

        @Override
        protected IsolatedEnumValueSnapshot deserialize(Decoder decoder) throws Exception {
            String className = decoder.readString();
            String name = decoder.readString();
            Class<? extends Enum<?>> enumClass = Cast.uncheckedCast(fromClassName(className));
            return new IsolatedEnumValueSnapshot(Enum.valueOf(Cast.uncheckedCast(enumClass), name));
        }

        @Override
        public Class<IsolatedEnumValueSnapshot> getIsolatableClass() {
            return IsolatedEnumValueSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return ENUM_VALUE;
        }
    }

    private abstract class AbstractIsolatedMapSerializer<T extends AbstractIsolatedMap<?>> extends IsolatableSerializer<T> {
        protected abstract T getIsolatedObject(ImmutableList<MapEntrySnapshot<Isolatable<?>>> entries);

        @Override
        protected void serialize(Encoder encoder, T value) throws Exception {
            List<MapEntrySnapshot<Isolatable<?>>> entrySnapshots = value.getEntries();
            encoder.writeInt(entrySnapshots.size());
            for (MapEntrySnapshot<Isolatable<?>> entrySnapshot : entrySnapshots) {
                writeIsolatable(encoder, entrySnapshot.getKey());
                writeIsolatable(encoder, entrySnapshot.getValue());
            }
        }

        @Override
        protected T deserialize(Decoder decoder) throws Exception {
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
        public byte getSerializerIndex() {
            return ISOLATED_MAP;
        }

        @Override
        protected IsolatedMap getIsolatedObject(ImmutableList<MapEntrySnapshot<Isolatable<?>>> entries) {
            return new IsolatedMap(entries);
        }
    }

    private class IsolatedPropertiesSerializer extends AbstractIsolatedMapSerializer<IsolatedProperties> {
        @Override
        protected IsolatedProperties getIsolatedObject(ImmutableList<MapEntrySnapshot<Isolatable<?>>> entries) {
            return new IsolatedProperties(entries);
        }

        @Override
        public Class<IsolatedProperties> getIsolatableClass() {
            return IsolatedProperties.class;
        }

        @Override
        public byte getSerializerIndex() {
            return ISOLATED_PROPERTIES;
        }
    }

    @NonNullApi
    private static class IsolatedArrayOfPrimitiveSerializer extends IsolatableSerializer<ArrayOfPrimitiveValueSnapshot> {

        @Override
        protected void serialize(Encoder encoder, ArrayOfPrimitiveValueSnapshot value) throws Exception {
            value.encode(encoder);
        }

        @Override
        protected ArrayOfPrimitiveValueSnapshot deserialize(Decoder decoder) throws Exception {
            return ArrayOfPrimitiveValueSnapshot.decode(decoder);
        }

        @Override
        public Class<ArrayOfPrimitiveValueSnapshot> getIsolatableClass() {
            return ArrayOfPrimitiveValueSnapshot.class;
        }

        @Override
        public byte getSerializerIndex() {
            return ISOLATED_ARRAY_OF_PRIMITIVE;
        }
    }

    private class IsolatedArraySerializer extends IsolatableSerializer<IsolatedArray> {

        @Override
        protected void serialize(Encoder encoder, IsolatedArray value) throws Exception {
            encoder.writeString(value.getArrayType().getName());
            writeIsolatableSequence(encoder, value.getElements());
        }

        @Override
        protected IsolatedArray deserialize(Decoder decoder) throws Exception {
            ImmutableList.Builder<Isolatable<?>> builder = ImmutableList.builder();
            Class<?> arrayType = fromClassName(decoder.readString());
            readIsolatableSequence(decoder, builder);
            return new IsolatedArray(builder.build(), arrayType);
        }

        @Override
        public Class<IsolatedArray> getIsolatableClass() {
            return IsolatedArray.class;
        }

        @Override
        public byte getSerializerIndex() {
            return ISOLATED_ARRAY;
        }
    }

    private class IsolatedListSerializer extends IsolatableSerializer<IsolatedList> {
        @Override
        protected void serialize(Encoder encoder, IsolatedList value) throws Exception {
            writeIsolatableSequence(encoder, value.getElements());
        }

        @Override
        protected IsolatedList deserialize(Decoder decoder) throws Exception {
            ImmutableList.Builder<Isolatable<?>> builder = ImmutableList.builder();
            readIsolatableSequence(decoder, builder);
            return new IsolatedList(builder.build());
        }

        @Override
        public Class<IsolatedList> getIsolatableClass() {
            return IsolatedList.class;
        }

        @Override
        public byte getSerializerIndex() {
            return ISOLATED_LIST;
        }
    }

    private class IsolatedSetSerializer extends IsolatableSerializer<IsolatedSet> {
        @Override
        protected void serialize(Encoder encoder, IsolatedSet value) throws Exception {
            writeIsolatableSequence(encoder, value.getElements());
        }

        @Override
        protected IsolatedSet deserialize(Decoder decoder) throws Exception {
            ImmutableSet.Builder<Isolatable<?>> builder = ImmutableSet.builder();
            readIsolatableSequence(decoder, builder);
            return new IsolatedSet(builder.build());
        }

        @Override
        public Class<IsolatedSet> getIsolatableClass() {
            return IsolatedSet.class;
        }

        @Override
        public byte getSerializerIndex() {
            return ISOLATED_SET;
        }
    }
}
