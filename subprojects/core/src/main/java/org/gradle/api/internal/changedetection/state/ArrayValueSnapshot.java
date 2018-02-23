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

import org.gradle.api.internal.changedetection.state.isolation.Isolatable;
import org.gradle.api.internal.changedetection.state.isolation.IsolationException;
import org.gradle.caching.internal.BuildCacheHasher;

import javax.annotation.Nullable;
import java.util.Arrays;

public class ArrayValueSnapshot implements ValueSnapshot, Isolatable<Object[]> {
    public static final ValueSnapshot EMPTY = new ArrayValueSnapshot(new ValueSnapshot[0]);
    private final ValueSnapshot[] elements;

    public ArrayValueSnapshot(ValueSnapshot[] elements) {
        this.elements = elements;
    }

    public ValueSnapshot[] getElements() {
        return elements;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putString("Array");
        hasher.putInt(elements.length);
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
            if (Arrays.equals(elements, otherArray.elements)) {
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
        return Arrays.equals(elements, other.elements);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

    @Override
    public Object[] isolate() {
        ValueSnapshot[] elements = getElements();
        Object[] toReturn = new Object[elements.length];
        for (int i = 0; i < elements.length; i++) {
            ValueSnapshot snapshot = elements[i];
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
