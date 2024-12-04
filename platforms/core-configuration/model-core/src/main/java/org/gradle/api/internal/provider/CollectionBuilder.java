/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.gradle.internal.Cast;

import java.util.Collection;

public interface CollectionBuilder<T, C> {
    void add(T element);

    default void addAll(Iterable<? extends T> elements) {
        elements.forEach(this::add);
    }

    C build();

    static <T, C extends Collection<T>> CollectionBuilder<T, C> of(C collection) {
        return new CollectionBuilder<T, C>() {
            @Override
            public void add(T element) {
                collection.add(element);
            }

            @Override
            public void addAll(Iterable<? extends T> elements) {
                Iterables.addAll(collection, elements);
            }

            @Override
            public C build() {
                return collection;
            }
        };
    }

    static <T> CollectionBuilder<T, ImmutableList<T>> of(ImmutableList.Builder<T> builder) {
        return Cast.uncheckedCast(of((ImmutableCollection.Builder<T>) builder));
    }

    static <T> CollectionBuilder<T, ImmutableSet<T>> of(ImmutableSet.Builder<T> builder) {
        return Cast.uncheckedCast(of((ImmutableCollection.Builder<T>) builder));
    }

    static <T> CollectionBuilder<T, ImmutableCollection<T>> of(ImmutableCollection.Builder<T> builder) {
        return new CollectionBuilder<T, ImmutableCollection<T>>() {
            @Override
            public void add(T element) {
                builder.add(element);
            }

            @Override
            public void addAll(Iterable<? extends T> elements) {
                builder.addAll(elements);
            }

            @Override
            public ImmutableCollection<T> build() {
                return builder.build();
            }
        };
    }
}
