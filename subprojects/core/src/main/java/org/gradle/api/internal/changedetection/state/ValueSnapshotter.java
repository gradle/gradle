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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ValueSnapshotter {
    private final ClassLoaderHierarchyHasher classLoaderHasher;

    public ValueSnapshotter(ClassLoaderHierarchyHasher classLoaderHasher) {
        this.classLoaderHasher = classLoaderHasher;
    }

    /**
     * Creates a snapshot of the given value.
     *
     * @throws UncheckedIOException On failure to snapshot the value.
     */
    public ValueSnapshot snapshot(Object value) throws UncheckedIOException {
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
                elements[i] = snapshot(element);
            }
            return new ListValueSnapshot(elements);
        }
        if (value instanceof Enum) {
            return new EnumValueSnapshot((Enum) value);
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
                builder.add(snapshot(element));
            }
            return new SetValueSnapshot(builder.build());
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            ImmutableMap.Builder<ValueSnapshot, ValueSnapshot> builder = new ImmutableMap.Builder<ValueSnapshot, ValueSnapshot>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                builder.put(snapshot(entry.getKey()), snapshot(entry.getValue()));
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
                elements[i] = snapshot(element);
            }
            return new ArrayValueSnapshot(elements);
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

    /**
     * Creates a snapshot of the given value, given a candidate snapshot. If the value is the same as the value provided by the candidate snapshot, the candidate _must_ be returned.
     */
    public ValueSnapshot snapshot(Object value, ValueSnapshot candidate) {
        return candidate.snapshot(value, this);
    }
}
