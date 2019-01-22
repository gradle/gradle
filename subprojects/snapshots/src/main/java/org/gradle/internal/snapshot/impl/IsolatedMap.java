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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.Pair;
import org.gradle.internal.isolation.Isolatable;
import org.gradle.internal.snapshot.ValueSnapshot;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

public class IsolatedMap extends AbstractMapSnapshot<Isolatable<?>> implements Isolatable<Map<Object, Object>> {
    public IsolatedMap(ImmutableList<Pair<Isolatable<?>, Isolatable<?>>> entries) {
        super(entries);
    }

    @Override
    public ValueSnapshot asSnapshot() {
        ImmutableList.Builder<Pair<ValueSnapshot, ValueSnapshot>> builder = ImmutableList.builderWithExpectedSize(entries.size());
        for (Pair<Isolatable<?>, Isolatable<?>> entry : entries) {
            builder.add(Pair.of(entry.left.asSnapshot(), entry.right.asSnapshot()));
        }
        return new MapValueSnapshot(builder.build());
    }

    @Override
    public Map<Object, Object> isolate() {
        Map<Object, Object> map = new LinkedHashMap<>(getEntries().size());
        for (Pair<Isolatable<?>, Isolatable<?>> entry : getEntries()) {
            map.put(entry.left.isolate(), entry.right.isolate());
        }
        return map;
    }

    @Nullable
    @Override
    public <S> S coerce(Class<S> type) {
        return null;
    }
}
