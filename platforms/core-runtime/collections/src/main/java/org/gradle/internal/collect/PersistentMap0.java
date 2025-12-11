/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.collect;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.gradle.internal.collect.Preconditions.entryCannotBeNull;

/// The [empty][PersistentMap#of()] [PersistentMap] implementation.
final class PersistentMap0 implements PersistentMap<Object, Object> {

    static final PersistentMap<Object, Object> INSTANCE = new PersistentMap0();

    private PersistentMap0() {
    }

    @Override
    public PersistentMap<Object, Object> assoc(Object key, Object value) {
        entryCannotBeNull(key, value);
        return new PersistentMap1<>(key, value);
    }

    @Override
    public PersistentMap<Object, Object> dissoc(Object key) {
        return this;
    }

    @Override
    public PersistentMap<Object, Object> modify(Object key, BiFunction<? super Object, ? super @Nullable Object, ?> function) {
        Object val = function.apply(key, null);
        return val != null
            ? assoc(key, val)
            : this;
    }

    @Override
    public @Nullable Object get(Object key) {
        return null;
    }

    @Override
    public Object getOrDefault(Object key, Object defaultValue) {
        return defaultValue;
    }

    @Override
    public boolean containsKey(Object key) {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public String toString() {
        return "{}";
    }

    @Override
    public Iterator<Map.Entry<Object, Object>> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public void forEach(Consumer<? super Map.Entry<Object, Object>> action) {
    }
}
