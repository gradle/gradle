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
import org.gradle.api.internal.changedetection.state.isolation.Isolatable;
import org.gradle.api.internal.changedetection.state.isolation.IsolationException;
import org.gradle.caching.internal.BuildCacheHasher;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

public class MapValueSnapshot implements ValueSnapshot, Isolatable<Map> {
    private final ImmutableMap<ValueSnapshot, ValueSnapshot> entries;

    public MapValueSnapshot(ImmutableMap<ValueSnapshot, ValueSnapshot> entries) {
        this.entries = entries;
    }

    public ImmutableMap<ValueSnapshot, ValueSnapshot> getEntries() {
        return entries;
    }

    @Override
    public void appendToHasher(BuildCacheHasher hasher) {
        hasher.putString("Map");
        hasher.putInt(entries.size());
        for (Map.Entry<ValueSnapshot, ValueSnapshot> entry : entries.entrySet()) {
            entry.getKey().appendToHasher(hasher);
            entry.getValue().appendToHasher(hasher);
        }
    }

    @Override
    public ValueSnapshot snapshot(Object value, ValueSnapshotter snapshotter) {
        ValueSnapshot newSnapshot = snapshotter.snapshot(value);
        if (equals(newSnapshot)) {
            return this;
        }
        return newSnapshot;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        MapValueSnapshot other = (MapValueSnapshot) obj;
        return entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public Map isolate() {
        Map map = new LinkedHashMap();
        for (Map.Entry<ValueSnapshot, ValueSnapshot> entry : entries.entrySet()) {
            if (entry.getKey() instanceof Isolatable) {
                if (entry.getValue() instanceof Isolatable) {
                    map.put(((Isolatable) entry.getKey()).isolate(), ((Isolatable) entry.getValue()).isolate());
                } else {
                    throw new IsolationException(entry.getValue());
                }
            } else {
                throw new IsolationException(entry.getKey());
            }
        }
        return map;
    }

    @Nullable
    @Override
    public <S> Isolatable<S> coerce(Class<S> type) {
        return null;
    }
}
