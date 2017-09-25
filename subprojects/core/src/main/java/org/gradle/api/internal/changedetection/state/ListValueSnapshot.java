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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ListValueSnapshot implements ValueSnapshot, Isolatable<List> {
    public static final ValueSnapshot EMPTY = new ListValueSnapshot(new ValueSnapshot[0]);

    private final ValueSnapshot[] elements;

    public ListValueSnapshot(ValueSnapshot[] elements) {
        this.elements = elements;
    }

    public ValueSnapshot[] getElements() {
        return elements;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putString("List");
        hasher.putInt(elements.length);
        for (ValueSnapshot element : elements) {
            element.appendToHasher(hasher);
        }
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        return processList(value, snapshotter);
    }

    private ValueSnapshot processList(Object value, ValueSnapshotter snapshotter) {
        if (!(value instanceof List)) {
            return snapshotter.snapshot(value);
        }

        // Find first position where values are different
        List<?> list = (List<?>) value;
        int pos = 0;
        int len = Math.min(elements.length, list.size());
        ValueSnapshot newElement = null;
        for (; pos < len; pos++) {
            ValueSnapshot element = elements[pos];
            newElement = snapshotter.snapshot(list.get(pos), element);
            if (element != newElement) {
                break;
            }
        }
        if (pos == elements.length && pos == list.size()) {
            // Same size and no differences
            return this;
        }

        // Copy the snapshots whose values are the same, then snapshot remaining values
        ValueSnapshot[] newElements = new ValueSnapshot[list.size()];
        System.arraycopy(elements, 0, newElements, 0, pos);
        if (pos < list.size()) {
            if (newElement != null) {
                newElements[pos] = newElement;
                pos++;
            }
            for (int i = pos; i < list.size(); i++) {
                newElements[i] = snapshotter.snapshot(list.get(i));
            }
        }

        return new ListValueSnapshot(newElements);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        ListValueSnapshot other = (ListValueSnapshot) obj;
        return Arrays.equals(elements, other.elements);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(elements);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List isolate() {
        List list = new ArrayList();
        ValueSnapshot[] elements = getElements();
        for (ValueSnapshot snapshot : elements) {
            if (snapshot instanceof Isolatable) {
                list.add(((Isolatable) snapshot).isolate());
            } else {
                throw new IsolationException(snapshot);
            }
        }
        return list;
    }

    @Nullable
    @Override
    public <S> Isolatable<S> coerce(Class<S> type) {
        return null;
    }
}
