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

import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

/**
 * A snapshot of an immutable scalar value. Should only be used for immutable JVM provided or core Gradle types.
 *
 * @param <T> the type of the value
 */
abstract class AbstractScalarValueSnapshot<T> implements ValueSnapshot {
    private final T value;

    public AbstractScalarValueSnapshot(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        if (this.value.equals(value)) {
            return this;
        }
        ValueSnapshot snapshot = snapshotter.snapshot(value);
        if (snapshot.equals(this)) {
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
        AbstractScalarValueSnapshot other = (AbstractScalarValueSnapshot) obj;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "" + value;
    }
}
