/*
 * Copyright 2021 the original author or authors.
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
import org.gradle.api.attributes.Attribute;
import org.gradle.internal.Cast;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.kryo.KryoBackedEncoder;
import org.gradle.internal.snapshot.ValueSnapshottingException;
import org.gradle.internal.state.Managed;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

abstract class AbstractValueProcessor {

    private final List<ValueSnapshotterSerializerRegistry> valueSnapshotterSerializerRegistryList;

    protected AbstractValueProcessor(List<ValueSnapshotterSerializerRegistry> valueSnapshotterSerializerRegistryList) {
        this.valueSnapshotterSerializerRegistryList = valueSnapshotterSerializerRegistryList;
    }

    protected <T> T processValue(@Nullable Object value, ValueVisitor<T> visitor) {
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
        Class<?> valueClass = value.getClass();
        if (valueClass.equals(File.class)) {
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
            ImmutableList.Builder<MapEntrySnapshot<T>> builder = ImmutableList.builderWithExpectedSize(map.size());
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                builder.add(new MapEntrySnapshot<>(processValue(entry.getKey(), visitor), processValue(entry.getValue(), visitor)));
            }
            if (value instanceof Properties) {
                return visitor.properties(builder.build());
            } else {
                return visitor.map(builder.build());
            }
        }
        if (valueClass.isArray()) {
            int length = Array.getLength(value);
            if (length == 0) {
                return visitor.emptyArray(valueClass.getComponentType());
            }
            ImmutableList.Builder<T> builder = ImmutableList.builderWithExpectedSize(length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                builder.add(processValue(element, visitor));
            }
            return visitor.array(builder.build(), valueClass.getComponentType());
        }
        if (value instanceof Attribute) {
            return visitor.attributeValue((Attribute<?>) value);
        }
        if (value instanceof Managed) {
            Managed managed = (Managed) value;
            if (managed.isImmutable()) {
                return visitor.managedImmutableValue(managed);
            } else {
                // May (or may not) be mutable - unpack the state
                T state = processValue(managed.unpackState(), visitor);
                return visitor.managedValue(managed, state);
            }
        }
        if (value instanceof Isolatable) {
            return visitor.fromIsolatable((Isolatable<?>) value);
        }
        if (value instanceof HashCode) {
            return visitor.hashCode((HashCode) value);
        }
        if (value instanceof ImplementationValue) {
            ImplementationValue implementationValue = (ImplementationValue) value;
            return visitor.implementationValue(implementationValue.getImplementationClassIdentifier(), implementationValue.getValue());
        }

        // Pluggable serialization
        for (ValueSnapshotterSerializerRegistry registry : valueSnapshotterSerializerRegistryList) {
            if (registry.canSerialize(valueClass)) {
                return gradleSerialization(value, registry.build(valueClass), visitor);
            }
        }

        // Fall back to Java serialization
        return javaSerialization(value, visitor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> T gradleSerialization(Object value, Serializer serializer, ValueVisitor<T> visitor) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (KryoBackedEncoder encoder = new KryoBackedEncoder(outputStream)) {
            serializer.write(encoder, Cast.uncheckedCast(value));
            encoder.flush();
        } catch (Exception e) {
            throw newValueSerializationException(value.getClass(), e);
        }
        return visitor.gradleSerialized(value, outputStream.toByteArray());
    }

    private <T> T javaSerialization(Object value, ValueVisitor<T> visitor) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectStr = new ObjectOutputStream(outputStream)) {
            objectStr.writeObject(value);
            objectStr.flush();
        } catch (IOException e) {
            throw newValueSerializationException(value.getClass(), e);
        }
        return visitor.javaSerialized(value, outputStream.toByteArray());
    }

    private ValueSnapshottingException newValueSerializationException(Class<?> valueType, Throwable cause) {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node("Could not serialize value of type ");
        formatter.appendType(valueType);
        return new ValueSnapshottingException(formatter.toString(), cause);
    }

    protected interface ValueVisitor<T> {

        T nullValue();

        T stringValue(String value);

        T booleanValue(Boolean value);

        T enumValue(Enum value);

        T classValue(Class<?> value);

        T implementationValue(String implementationClassIdentifier, Object implementation);

        T fileValue(File value);

        T integerValue(Integer value);

        T longValue(Long value);

        T shortValue(Short value);

        T hashCode(HashCode value);

        T attributeValue(Attribute<?> value);

        T managedValue(Managed value, T state);

        T managedImmutableValue(Managed managed);

        T fromIsolatable(Isolatable<?> value);

        T emptyArray(Class<?> arrayType);

        T array(ImmutableList<T> elements, Class<?> arrayType);

        T emptyList();

        T list(ImmutableList<T> elements);

        T set(ImmutableSet<T> elements);

        T map(ImmutableList<MapEntrySnapshot<T>> elements);

        T properties(ImmutableList<MapEntrySnapshot<T>> elements);

        T gradleSerialized(Object value, byte[] serializedValue);

        T javaSerialized(Object value, byte[] serializedValue);
    }
}
