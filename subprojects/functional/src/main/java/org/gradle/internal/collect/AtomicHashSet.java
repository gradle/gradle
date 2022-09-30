/*
 * Copyright 2022 the original author or authors.
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

import io.usethesource.capsule.Set;

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicHashSet<T> implements Iterable<T> {

    private AtomicReference<Set.Immutable<T>> set = new AtomicReference<>(Set.Immutable.of());

    public boolean add(T value) {
        boolean[] added = new boolean[1];
        set.updateAndGet(set -> {
            Set.Immutable<T> result = set.__insert(value);
            added[0] = result != set;
            return result;
        });
        return added[0];
    }

    public boolean contains(T value) {
        return set.get().contains(value);
    }

    public void remove(T value) {
        set.updateAndGet(set -> set.__remove(value));
    }

    public void clear() {
        set.set(Set.Immutable.of());
    }

    @Override
    public Iterator<T> iterator() {
        return set.get().iterator();
    }

}
