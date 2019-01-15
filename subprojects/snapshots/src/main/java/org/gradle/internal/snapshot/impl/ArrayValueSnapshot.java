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
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.isolation.IsolationException;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;

import javax.annotation.Nullable;
import java.util.List;

public class ArrayValueSnapshot implements ValueSnapshot, Isolatable<Object[]> {
    public static final ArrayValueSnapshot EMPTY = new ArrayValueSnapshot(ImmutableList.of());
    private final ImmutableList<ValueSnapshot> elements;

    public ArrayValueSnapshot(ImmutableList<ValueSnapshot> elements) {
        this.elements = elements;
    }

    public List<ValueSnapshot> getElements() {
        return elements;
    }

    @Override
    public void appendToHasher(Hasher hasher) {
        hasher.putString("Array");
        hasher.putInt(elements.size());
        for (ValueSnapshot element : elements) {
            element.appendToHasher(hasher);
        }
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot other = snapshotter.snapshot(value);
        if (isEqualArrayValueSnapshot(other)) {
            return this;
        }
        return other;
    }

    private boolean isEqualArrayValueSnapshot(ValueSnapshot other) {
        if (other instanceof ArrayValueSnapshot) {
            ArrayValueSnapshot otherArray = (ArrayValueSnapshot) other;
            if (elements.equals(otherArray.elements)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        ArrayValueSnapshot other = (ArrayValueSnapshot) obj;
        return elements.equals(other.elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    @Override
    public Object[] isolate() {
        Object[] toReturn = new Object[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            ValueSnapshot snapshot = elements.get(i);
            if (snapshot instanceof Isolatable) {
                toReturn[i] = ((Isolatable) snapshot).isolate();
            } else {
                throw new IsolationException(snapshot);
            }
        }
        return toReturn;
    }

    @Nullable
    @Override
    public <S> Isolatable<S> coerce(Class<S> type) {
        return null;
    }
}
