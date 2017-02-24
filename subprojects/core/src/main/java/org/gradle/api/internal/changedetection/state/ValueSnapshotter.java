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

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.List;

public class ValueSnapshotter {
    private final ClassLoaderHierarchyHasher classLoaderHasher;

    public ValueSnapshotter(ClassLoaderHierarchyHasher classLoaderHasher) {
        this.classLoaderHasher = classLoaderHasher;
    }

    /**
     * Creates a snapshot of the given value.
     */
    public ValueSnapshot snapshot(Object value) {
        if (value == null) {
            return NullValueSnapshot.INSTANCE;
        }
        if (value instanceof String) {
            String str = (String) value;
            return new StringValueSnapshot(str);
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            ValueSnapshot[] elements = new ValueSnapshot[list.size()];
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                elements[i] = snapshot(element);
            }
            return new ListValueSnapshot(elements);
        }

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
