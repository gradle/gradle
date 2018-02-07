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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.changedetection.state.isolation.Isolatable;
import org.gradle.api.internal.changedetection.state.isolation.IsolationException;
import org.gradle.caching.internal.BuildCacheHasher;

import javax.annotation.Nullable;
import java.util.LinkedHashSet;
import java.util.Set;

public class SetValueSnapshot implements ValueSnapshot, Isolatable<Set> {
    private final ImmutableSet<ValueSnapshot> elements;

    public SetValueSnapshot(ImmutableSet<ValueSnapshot> elements) {
        this.elements = elements;
    }

    public ImmutableSet<ValueSnapshot> getElements() {
        return elements;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putString("Set");
        hasher.putInt(elements.size());
        for (ValueSnapshot element : elements) {
            element.appendToHasher(hasher);
        }
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot newSnapshot = snapshotter.snapshot(value);
        if (isEqualSetValueSnapshot(newSnapshot)) {
            return this;
        }
        return newSnapshot;
    }

    private boolean isEqualSetValueSnapshot(ValueSnapshot newSnapshot) {
        if (newSnapshot instanceof SetValueSnapshot) {
            SetValueSnapshot other = (SetValueSnapshot) newSnapshot;
            if (elements.equals(other.elements)) {
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
        SetValueSnapshot other = (SetValueSnapshot) obj;
        return elements.equals(other.elements);
    }

    @Override
    public int hashCode() {
        return elements.hashCode();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set isolate() {
        Set set = new LinkedHashSet();
        for (ValueSnapshot snapshot : elements) {
            if (snapshot instanceof Isolatable) {
                set.add(((Isolatable) snapshot).isolate());
            } else {
                throw new IsolationException(snapshot);
            }
        }
        return set;
    }

    @Nullable
    @Override
    public <S> Isolatable<S> coerce(Class<S> type) {
        return null;
    }
}
