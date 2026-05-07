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

import java.util.Collections;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.gradle.internal.collect.Preconditions.keyCannotBeNull;

/// An empty [PersistentSet].
///
final class PersistentSet0 implements PersistentSet<Object> {

    static final PersistentSet<Object> INSTANCE = new PersistentSet0();

    private PersistentSet0() {
    }

    @Override
    public PersistentSet<Object> plus(Object key) {
        keyCannotBeNull(key);
        return new PersistentSet1<>(key);
    }

    @Override
    public PersistentSet<Object> minus(Object key) {
        return this;
    }

    @Override
    public PersistentSet<Object> minusAll(Iterable<Object> keys) {
        return this;
    }

    @Override
    public PersistentSet<Object> except(PersistentSet<Object> other) {
        return this;
    }

    @Override
    public PersistentSet<Object> filter(Predicate<? super Object> predicate) {
        return this;
    }

    @Override
    public <R> PersistentSet<R> map(Function<? super Object, ? extends R> mapper) {
        return PersistentSet.of();
    }

    @Override
    public <R> PersistentSet<R> flatMap(Function<? super Object, PersistentSet<R>> mapper) {
        return PersistentSet.of();
    }

    @Override
    public boolean anyMatch(Predicate<? super Object> predicate) {
        return false;
    }

    @Override
    public boolean noneMatch(Predicate<? super Object> predicate) {
        return true;
    }

    @Override
    public <G> PersistentMap<G, PersistentSet<Object>> groupBy(Function<? super Object, ? extends G> group) {
        return PersistentMap.of();
    }

    @Override
    public boolean contains(Object key) {
        return false;
    }

    @Override
    public int size() {
        return 0;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> PersistentSet<Object> union(PersistentSet<R> other) {
        return (PersistentSet<Object>) other;
    }

    @Override
    public PersistentSet<Object> intersect(PersistentSet<Object> other) {
        return this;
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
    public Iterator<Object> iterator() {
        return Collections.emptyIterator();
    }

    @Override
    public void forEach(Consumer<? super Object> action) {
    }
}
