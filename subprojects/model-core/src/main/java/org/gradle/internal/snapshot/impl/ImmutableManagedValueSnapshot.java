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

package org.gradle.internal.snapshot.impl;

import org.gradle.internal.hash.Hasher;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;
import java.util.Objects;

public class ImmutableManagedValueSnapshot implements ValueSnapshot {
    private final String className;

    @Nullable
    private final String value;

    public ImmutableManagedValueSnapshot(String className, @Nullable String value) {
        this.className = className;
        this.value = value;
    }

    public String getClassName() {
        return className;
    }

    @Nullable
    public String getValue() {
        return value;
    }

    @Override
    public ValueSnapshot snapshot(@Nullable Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot snapshot = snapshotter.snapshot(value);
        if (equals(snapshot)) {
            return this;
        }
        return snapshot;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        ImmutableManagedValueSnapshot other = (ImmutableManagedValueSnapshot) obj;
        return other.className.equals(className) && Objects.equals(other.value, value);
    }

    @Override
    public int hashCode() {
        return className.hashCode() ^ Objects.hashCode(value);
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putString(className);
        if (value != null) {
            hasher.putString(value);
        } else {
            hasher.putNull();
        }
    }

    @Override
    public String toString() {
        return "(" + className + ") " + value;
    }
}
