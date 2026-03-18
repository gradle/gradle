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

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.gradle.internal.collect.Preconditions.keyCannotBeNull;

/// A [PersistentSet] with a single element.
///
final class PersistentSet1<K> implements PersistentSet<K> {

    private final K key;

    public PersistentSet1(K key) {
        this.key = key;
    }

    @Override
    public PersistentSet<K> plus(K key) {
        keyCannotBeNull(key);
        return contains(key)
            ? this
            : PersistentSetTrie.ofDistinct(this.key, key);
    }

    @Override
    public PersistentSet<K> minus(K key) {
        return clear(contains(key));
    }

    @Override
    public PersistentSet<K> minusAll(Iterable<K> keys) {
        if (keys instanceof PersistentSet<?>) {
            return clear(((PersistentSet<K>) keys).contains(key));
        }
        if (keys instanceof Collection<?>) {
            return clear(((Collection<?>) keys).contains(key));
        }
        for (K k : keys) {
            if (contains(k)) {
                return clear(true);
            }
        }
        return this;
    }

    @Override
    public PersistentSet<K> except(PersistentSet<K> other) {
        return clear(other.contains(key));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <S extends K> PersistentSet<K> union(PersistentSet<S> other) {
        return other.isEmpty()
            ? this
            : (PersistentSet<K>) other.plus((S) key);
    }

    @Override
    public PersistentSet<K> intersect(PersistentSet<K> other) {
        return clear(!other.contains(key));
    }

    private PersistentSet<K> clear(boolean removed) {
        return removed
            ? PersistentSet.of()
            : this;
    }

    @Override
    public PersistentSet<K> filter(Predicate<? super K> predicate) {
        return predicate.test(key)
            ? this
            : PersistentSet.of();
    }

    @Override
    public <R> PersistentSet<R> map(Function<? super K, ? extends R> mapper) {
        return PersistentSet.of(mapper.apply(key));
    }

    @Override
    public <R> PersistentSet<R> flatMap(Function<? super K, PersistentSet<R>> mapper) {
        return mapper.apply(key);
    }

    @Override
    public <G> PersistentMap<G, PersistentSet<K>> groupBy(Function<? super K, ? extends @Nullable G> group) {
        G g = group.apply(key);
        return g != null
            ? PersistentMap.of(g, this)
            : PersistentMap.of();
    }

    @Override
    public boolean anyMatch(Predicate<? super K> predicate) {
        return predicate.test(key);
    }

    @Override
    public boolean noneMatch(Predicate<? super K> predicate) {
        return !predicate.test(key);
    }

    @Override
    public boolean contains(K key) {
        return Objects.equals(key, this.key);
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public Iterator<K> iterator() {
        return new SingletonIterator<>(key);
    }

    @Override
    public void forEach(Consumer<? super K> action) {
        action.accept(key);
    }

    @Override
    public String toString() {
        return "{" + key + "}";
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        }
        if (other == null) {
            return false;
        }
        if (!(other instanceof PersistentSet<?>)) {
            return false;
        }
        PersistentSet<K> that = (PersistentSet<K>) other;
        return that.size() == 1
            && that.contains(this.key);
    }
}
