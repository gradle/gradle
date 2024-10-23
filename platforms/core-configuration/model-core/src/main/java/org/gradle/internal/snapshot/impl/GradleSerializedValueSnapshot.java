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
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * An immutable snapshot of the state of some value.
 */
public class GradleSerializedValueSnapshot implements ValueSnapshot {

    private final HashCode implementationHash;
    private final byte[] serializedValue;

    public GradleSerializedValueSnapshot(
        @Nullable HashCode implementationHash,
        byte[] serializedValue
    ) {
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
        if (hasSameSerializedValue(snapshot)) {
            return this;
        }
        return snapshot;
    }

    private boolean hasSameSerializedValue(ValueSnapshot snapshot) {
        if (snapshot instanceof GradleSerializedValueSnapshot) {
            GradleSerializedValueSnapshot newSnapshot = (GradleSerializedValueSnapshot) snapshot;
            if (!Objects.equal(implementationHash, newSnapshot.implementationHash)) {
                // Different implementation - assume value has changed
                return false;
            }
            if (Arrays.equals(serializedValue, newSnapshot.serializedValue)) {
                // Same serialized content - value has not changed
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
        GradleSerializedValueSnapshot other = (GradleSerializedValueSnapshot) obj;
        return Objects.equal(implementationHash, other.implementationHash) && Arrays.equals(serializedValue, other.serializedValue);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(serializedValue);
    }
}
