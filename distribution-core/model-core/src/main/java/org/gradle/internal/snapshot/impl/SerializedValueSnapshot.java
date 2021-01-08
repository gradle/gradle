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

import com.google.common.base.Objects;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.ValueSnapshottingException;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.util.Arrays;

/**
 * An immutable snapshot of the state of some value.
 */
public class SerializedValueSnapshot implements ValueSnapshot {
    private final HashCode implementationHash;
    private final byte[] serializedValue;

    public SerializedValueSnapshot(@Nullable HashCode implementationHash, byte[] serializedValue) {
        this.implementationHash = implementationHash;
        this.serializedValue = serializedValue;
    }

    @Nullable
    public HashCode getImplementationHash() {
        return implementationHash;
    }

    public byte[] getValue() {
        return serializedValue;
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot snapshot = snapshotter.snapshot(value);
        if (hasSameSerializedValue(value, snapshot)) {
            return this;
        }
        return snapshot;
    }

    private boolean hasSameSerializedValue(Object value, ValueSnapshot snapshot) {
        if (snapshot instanceof SerializedValueSnapshot) {
            SerializedValueSnapshot newSnapshot = (SerializedValueSnapshot) snapshot;
            if (!Objects.equal(implementationHash, newSnapshot.implementationHash)) {
                // Different implementation - assume value has changed
                return false;
            }
            if (Arrays.equals(serializedValue, newSnapshot.serializedValue)) {
                // Same serialized content - value has not changed
                return true;
            }

            // Deserialize the old value and use the equals() implementation. This will be removed at some point
            Object oldValue = populateClass(value.getClass());
            if (oldValue.equals(value)) {
                // Same value
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        if (implementationHash == null) {
            hasher.putNull();
        } else {
            hasher.putHash(implementationHash);
        }
        hasher.putBytes(serializedValue);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        SerializedValueSnapshot other = (SerializedValueSnapshot) obj;
        return Objects.equal(implementationHash, other.implementationHash) && Arrays.equals(serializedValue, other.serializedValue);
    }

    protected Object populateClass(Class<?> originalClass) {
        Object populated;
        try {
            populated = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedValue), originalClass.getClassLoader()).readObject();
        } catch (Exception e) {
            throw new ValueSnapshottingException("Couldn't populate class " + originalClass.getName(), e);
        }
        return populated;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(serializedValue);
    }
}
