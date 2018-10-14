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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Named;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolationException;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotStrategy;
import org.gradle.internal.snapshot.ValueSnapshottable;
import org.gradle.internal.snapshot.ValueSnapshotter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultValueSnapshotter implements ValueSnapshotter {
    private final ClassLoaderHierarchyHasher classLoaderHasher;
    private final NamedObjectInstantiator namedObjectInstantiator;
    private final ValueSnapshotStrategy valueSnapshotStrategy;
    private final IsolatableValueSnapshotStrategy isolatedSnapshotStrategy;

    public DefaultValueSnapshotter(ClassLoaderHierarchyHasher classLoaderHasher, NamedObjectInstantiator namedObjectInstantiator) {
        this.classLoaderHasher = classLoaderHasher;
        this.namedObjectInstantiator = namedObjectInstantiator;
        valueSnapshotStrategy = new ValueSnapshotStrategy(this);
        isolatedSnapshotStrategy = new IsolatableValueSnapshotStrategy(this);
    }

    @Override
    public ValueSnapshot snapshot(Object value) throws UncheckedIOException {
        return processValue(value, valueSnapshotStrategy);
    }

    @Override
    public <T> Isolatable<T> isolate(T value) {
        try {
            return Cast.uncheckedCast(isolatableSnapshot(value));
        } catch (Throwable t) {
            throw new IsolationException(value, t);
        }
    }

    @Override
    public ValueSnapshot isolatableSnapshot(Object value) throws UncheckedIOException {
        ValueSnapshot possible = processValue(value, isolatedSnapshotStrategy);
        if (possible instanceof Isolatable) {
            return possible;
        } else {
            return wrap(value, possible);
        }
    }

    private ValueSnapshot processValue(Object value, ValueSnapshotStrategy strategy) {
        if (value == null) {
            return NullValueSnapshot.INSTANCE;
        }
        if (value instanceof String) {
            return new StringValueSnapshot((String) value);
        }
        if (value instanceof Boolean) {
            return value.equals(Boolean.TRUE) ? BooleanValueSnapshot.TRUE : BooleanValueSnapshot.FALSE;
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.size() == 0) {
                return ListValueSnapshot.EMPTY;
            }
            ValueSnapshot[] elements = new ValueSnapshot[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                elements[i] = strategy.snapshot(element);
            }
            return new ListValueSnapshot(elements);
        }
        if (value instanceof Enum) {
            return new EnumValueSnapshot((Enum) value);
        }
        if (value instanceof Class<?>) {
            Class<?> implementation = (Class<?>) value;
            return ImplementationSnapshot.of(implementation, classLoaderHasher);
        }
        if (value.getClass().equals(File.class)) {
            // Not subtypes as we don't know whether they are immutable or not
            return new FileValueSnapshot((File) value);
        }
        if (value instanceof Number) {
            if (value instanceof Integer) {
                return new IntegerValueSnapshot((Integer) value);
            }
            if (value instanceof Long) {
                return new LongValueSnapshot((Long) value);
            }
            if (value instanceof Short) {
                return new ShortValueSnapshot((Short) value);
            }
        }
        if (value instanceof Set) {
            Set<?> set = (Set<?>) value;
            ImmutableSet.Builder<ValueSnapshot> builder = ImmutableSet.builder();
            for (Object element : set) {
                builder.add(strategy.snapshot(element));
            }
            return new SetValueSnapshot(builder.build());
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            ImmutableMap.Builder<ValueSnapshot, ValueSnapshot> builder = new ImmutableMap.Builder<ValueSnapshot, ValueSnapshot>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                builder.put(strategy.snapshot(entry.getKey()), strategy.snapshot(entry.getValue()));
            }
            return new MapValueSnapshot(builder.build());
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            if (length == 0) {
                return ArrayValueSnapshot.EMPTY;
            }
            ValueSnapshot[] elements = new ValueSnapshot[length];
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                elements[i] = strategy.snapshot(element);
            }
            return new ArrayValueSnapshot(elements);
        }
        if (value instanceof Attribute) {
            return new AttributeDefinitionSnapshot((Attribute<?>) value, classLoaderHasher);
        }
        if (value instanceof Provider) {
            Provider<?> provider = (Provider) value;
            ValueSnapshot valueSnapshot = strategy.snapshot(provider.get());
            return new ProviderSnapshot(valueSnapshot);
        }
        if (value instanceof NamedObjectInstantiator.Managed) {
            return new ManagedNamedTypeSnapshot((Named)value);
        }
        if (value instanceof ValueSnapshottable) {
            return ((ValueSnapshottable) value).snapshot();
        }
        if (value instanceof ValueSnapshot) {
            return (ValueSnapshot) value;
        }

        // Fall back to serialization
        return serialize(value);
    }

    private SerializedValueSnapshot serialize(Object value) {
        ByteArrayOutputStream outputStream;
        try {
            outputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectStr = new ObjectOutputStream(outputStream);
            objectStr.writeObject(value);
            objectStr.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new SerializedValueSnapshot(classLoaderHasher.getClassLoaderHash(value.getClass().getClassLoader()), outputStream.toByteArray());
    }

    private ValueSnapshot wrap(Object value, ValueSnapshot possible) {
        if (possible instanceof EnumValueSnapshot) {
            return new IsolatableEnumValueSnapshot((Enum) value);
        }
        if (possible instanceof SerializedValueSnapshot) {
            SerializedValueSnapshot original = (SerializedValueSnapshot) possible;
            return new IsolatableSerializedValueSnapshot(original.getImplementationHash(), original.getValue(), value.getClass());
        }
        if (possible instanceof ManagedNamedTypeSnapshot) {
            return new IsolatedManagedNamedTypeSnapshot((Named) value, namedObjectInstantiator);
        }
        throw new IsolationException(value);
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshot candidate) {
        return candidate.snapshot(value, this);
    }
}
