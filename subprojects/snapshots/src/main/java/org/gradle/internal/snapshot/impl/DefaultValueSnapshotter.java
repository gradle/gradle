/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.Pair;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.Managed;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.isolation.IsolationException;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultValueSnapshotter implements ValueSnapshotter, IsolatableFactory {
    private final ValueVisitor<ValueSnapshot> valueSnapshotValueVisitor;
    private final ValueVisitor<Isolatable<?>> isolatableValueVisitor;

    public DefaultValueSnapshotter(ClassLoaderHierarchyHasher classLoaderHasher, NamedObjectInstantiator namedObjectInstantiator) {
        valueSnapshotValueVisitor = new ValueSnapshotVisitor(classLoaderHasher);
        isolatableValueVisitor = new IsolatableVisitor(classLoaderHasher, namedObjectInstantiator);
    }

    @Override
    public ValueSnapshot snapshot(@Nullable Object value) throws UncheckedIOException {
        return processValue(value, valueSnapshotValueVisitor);
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshot candidate) {
        return candidate.snapshot(value, this);
    }

    @Override
    public <T> Isolatable<T> isolate(@Nullable T value) {
        try {
            return Cast.uncheckedCast(processValue(value, isolatableValueVisitor));
        } catch (Throwable t) {
            throw new IsolationException(value, t);
        }
    }

    private <T> T processValue(@Nullable Object value, ValueVisitor<T> visitor) {
        if (value == null) {
            return visitor.nullValue();
        }
        if (value instanceof String) {
            return visitor.stringValue((String) value);
        }
        if (value instanceof Boolean) {
            return visitor.booleanValue((Boolean) value);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() == 0) {
                return visitor.emptyList();
            }
            ImmutableList.Builder<T> builder = ImmutableList.builderWithExpectedSize(list.size());
            for (Object element : list) {
                builder.add(processValue(element, visitor));
            }
            return visitor.list(builder.build());
        }
        if (value instanceof Enum) {
            return visitor.enumValue((Enum) value);
        }
        if (value instanceof Class<?>) {
            return visitor.classValue((Class<?>) value);
        }
        if (value.getClass().equals(File.class)) {
            // Not subtypes as we don't know whether they are immutable or not
            return visitor.fileValue((File) value);
        }
        if (value instanceof Number) {
            if (value instanceof Integer) {
                return visitor.integerValue((Integer) value);
            }
            if (value instanceof Long) {
                return visitor.longValue((Long) value);
            }
            if (value instanceof Short) {
                return visitor.shortValue((Short) value);
            }
        }
        if (value instanceof Set) {
            Set<?> set = (Set<?>) value;
            ImmutableSet.Builder<T> builder = ImmutableSet.builderWithExpectedSize(set.size());
            for (Object element : set) {
                builder.add(processValue(element, visitor));
            }
            return visitor.set(builder.build());
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            ImmutableList.Builder<Pair<T, T>> builder = ImmutableList.builderWithExpectedSize(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                builder.add(Pair.of(processValue(entry.getKey(), visitor), processValue(entry.getValue(), visitor)));
            }
            return visitor.map(builder.build());
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            if (length == 0) {
                return visitor.emptyArray();
            }
            ImmutableList.Builder<T> builder = ImmutableList.builderWithExpectedSize(length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                builder.add(processValue(element, visitor));
            }
            return visitor.array(builder.build());
        }
        if (value instanceof Attribute) {
            return visitor.attributeValue((Attribute<?>) value);
        }
        if (value instanceof Provider) {
            Provider<?> provider = (Provider) value;
            Object providerValue = provider.get();
            T providerValueSnapshot = processValue(providerValue, visitor);
            return visitor.provider(providerValue, providerValueSnapshot);
        }
        if (value instanceof Managed) {
            Managed managed = (Managed) value;
            if (managed.immutable()) {
                return visitor.managedImmutableValue(managed);
            } else {
                // May be mutable - unpack the state
                T state = processValue(managed.unpackState(), visitor);
                return visitor.managedValue(managed, state);
            }
        }
        if (value instanceof Isolatable) {
            return visitor.fromIsolatable((Isolatable<?>) value);
        }

        // Fall back to serialization
        return serialize(value, visitor);
    }

    private <T> T serialize(Object value, ValueVisitor<T> visitor) {
        ByteArrayOutputStream outputStream;
        try {
            outputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStr = new ObjectOutputStream(outputStream);
            objectStr.writeObject(value);
            objectStr.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return visitor.serialized(value, outputStream.toByteArray());
    }

    private interface ValueVisitor<T> {
        T nullValue();

        T stringValue(String value);

        T booleanValue(Boolean value);

        T enumValue(Enum value);

        T classValue(Class<?> value);

        T fileValue(File value);

        T integerValue(Integer value);

        T longValue(Long value);

        T shortValue(Short value);

        T attributeValue(Attribute<?> value);

        T managedValue(Managed value, T state);

        T managedImmutableValue(Managed managed);

        T fromIsolatable(Isolatable<?> value);

        T emptyArray();

        T array(ImmutableList<T> elements);

        T emptyList();

        T list(ImmutableList<T> elements);

        T set(ImmutableSet<T> elements);

        T map(ImmutableList<Pair<T, T>> elements);

        T provider(Object value, T snapshot);

        T serialized(Object value, byte[] serializedValue);
    }

    private static class ValueSnapshotVisitor implements ValueVisitor<ValueSnapshot> {
        private final ClassLoaderHierarchyHasher classLoaderHasher;

        ValueSnapshotVisitor(ClassLoaderHierarchyHasher classLoaderHasher) {
            this.classLoaderHasher = classLoaderHasher;
        }

        @Override
        public ValueSnapshot nullValue() {
            return NullValueSnapshot.INSTANCE;
        }

        @Override
        public ValueSnapshot stringValue(String value) {
            return new StringValueSnapshot(value);
        }

        @Override
        public ValueSnapshot booleanValue(Boolean value) {
            return value.equals(Boolean.TRUE) ? BooleanValueSnapshot.TRUE : BooleanValueSnapshot.FALSE;
        }

        @Override
        public ValueSnapshot integerValue(Integer value) {
            return new IntegerValueSnapshot(value);
        }

        @Override
        public ValueSnapshot longValue(Long value) {
            return new LongValueSnapshot(value);
        }

        @Override
        public ValueSnapshot shortValue(Short value) {
            return new ShortValueSnapshot(value);
        }

        @Override
        public ValueSnapshot enumValue(Enum value) {
            return new EnumValueSnapshot(value);
        }

        @Override
        public ValueSnapshot classValue(Class<?> value) {
            return ImplementationSnapshot.of(value, classLoaderHasher);
        }

        @Override
        public ValueSnapshot fileValue(File value) {
            return new FileValueSnapshot(value);
        }

        @Override
        public ValueSnapshot attributeValue(Attribute<?> value) {
            return new AttributeDefinitionSnapshot(value, classLoaderHasher);
        }

        @Override
        public ValueSnapshot managedImmutableValue(Managed managed) {
            return new ImmutableManagedValueSnapshot(managed.publicType().getName(), (String) managed.unpackState());
        }

        @Override
        public ValueSnapshot managedValue(Managed value, ValueSnapshot state) {
            return new ManagedTypeSnapshot(value.publicType().getName(), state);
        }

        @Override
        public ValueSnapshot fromIsolatable(Isolatable<?> value) {
            return value.asSnapshot();
        }

        @Override
        public ValueSnapshot serialized(Object value, byte[] serializedValue) {
            return new SerializedValueSnapshot(classLoaderHasher.getClassLoaderHash(value.getClass().getClassLoader()), serializedValue);
        }

        @Override
        public ValueSnapshot emptyArray() {
            return ArrayValueSnapshot.EMPTY;
        }

        @Override
        public ValueSnapshot array(ImmutableList<ValueSnapshot> elements) {
            return new ArrayValueSnapshot(elements);
        }

        @Override
        public ValueSnapshot emptyList() {
            return ListValueSnapshot.EMPTY;
        }

        @Override
        public ValueSnapshot list(ImmutableList<ValueSnapshot> elements) {
            return new ListValueSnapshot(elements);
        }

        @Override
        public ValueSnapshot set(ImmutableSet<ValueSnapshot> elements) {
            return new SetValueSnapshot(elements);
        }

        @Override
        public ValueSnapshot map(ImmutableList<Pair<ValueSnapshot, ValueSnapshot>> elements) {
            return new MapValueSnapshot(elements);
        }

        @Override
        public ValueSnapshot provider(Object value, ValueSnapshot snapshot) {
            return new ProviderSnapshot(snapshot);
        }
    }

    private static class IsolatableVisitor implements ValueVisitor<Isolatable<?>> {
        private final ClassLoaderHierarchyHasher classLoaderHasher;
        private final NamedObjectInstantiator namedObjectInstantiator;

        IsolatableVisitor(ClassLoaderHierarchyHasher classLoaderHasher, NamedObjectInstantiator namedObjectInstantiator) {
            this.classLoaderHasher = classLoaderHasher;
            this.namedObjectInstantiator = namedObjectInstantiator;
        }

        @Override
        public Isolatable<?> nullValue() {
            return NullValueSnapshot.INSTANCE;
        }

        @Override
        public Isolatable<?> stringValue(String value) {
            return new StringValueSnapshot(value);
        }

        @Override
        public Isolatable<?> booleanValue(Boolean value) {
            return value.equals(Boolean.TRUE) ? BooleanValueSnapshot.TRUE : BooleanValueSnapshot.FALSE;
        }

        @Override
        public Isolatable<?> integerValue(Integer value) {
            return new IntegerValueSnapshot(value);
        }

        @Override
        public Isolatable<?> longValue(Long value) {
            return new LongValueSnapshot(value);
        }

        @Override
        public Isolatable<?> shortValue(Short value) {
            return new ShortValueSnapshot(value);
        }

        @Override
        public Isolatable<?> enumValue(Enum value) {
            return new IsolatableEnumValueSnapshot(value);
        }

        @Override
        public Isolatable<?> classValue(Class<?> value) {
            throw new IsolationException(value);
        }

        @Override
        public Isolatable<?> fileValue(File value) {
            return new FileValueSnapshot(value);
        }

        @Override
        public Isolatable<?> attributeValue(Attribute<?> value) {
            return new AttributeDefinitionSnapshot(value, classLoaderHasher);
        }

        @Override
        public Isolatable<?> managedImmutableValue(Managed managed) {
            return new IsolatedImmutableManagedValue(managed);
        }

        @Override
        public Isolatable<?> managedValue(Managed value, Isolatable<?> state) {
            return new IsolatedManagedTypeSnapshot(value.publicType(), value.managedFactory(), state);
        }

        @Override
        public Isolatable<?> fromIsolatable(Isolatable<?> value) {
            return value;
        }

        @Override
        public Isolatable<?> serialized(Object value, byte[] serializedValue) {
            return new IsolatableSerializedValueSnapshot(classLoaderHasher.getClassLoaderHash(value.getClass().getClassLoader()), serializedValue, value.getClass());
        }

        @Override
        public Isolatable<?> provider(Object value, Isolatable<?> snapshot) {
            throw new IsolationException(value);
        }

        @Override
        public Isolatable<?> emptyArray() {
            return IsolatedArray.EMPTY;
        }

        @Override
        public Isolatable<?> array(ImmutableList<Isolatable<?>> elements) {
            return new IsolatedArray(elements);
        }

        @Override
        public Isolatable<?> emptyList() {
            return IsolatedList.EMPTY;
        }

        @Override
        public Isolatable<?> list(ImmutableList<Isolatable<?>> elements) {
            return new IsolatedList(elements);
        }

        @Override
        public Isolatable<?> set(ImmutableSet<Isolatable<?>> elements) {
            return new IsolatedSet(elements);
        }

        @Override
        public Isolatable<?> map(ImmutableList<Pair<Isolatable<?>, Isolatable<?>>> elements) {
            return new IsolatedMap(elements);
        }
    }
}
