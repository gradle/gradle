/*
 * Copyright 2026 the original author or authors.
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

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * A thread-safe primitive long hash set.
 *
 * Backed by fastutil's {@link LongOpenHashSet} with synchronized access.
 * Zero per-element object allocation — only allocates when the backing
 * array needs to grow.
 */
public class SynchronizedLongHashSet {

    private final LongOpenHashSet delegate = new LongOpenHashSet();

    public synchronized void add(long id) {
        delegate.add(id);
    }

    public synchronized boolean contains(long id) {
        return delegate.contains(id);
    }

    /**
     * Removes the given value from the set.
     *
     * @return true if the value was present and removed, false if not found
     */
    public synchronized boolean remove(long id) {
        return delegate.remove(id);
    }

    public synchronized int size() {
        return delegate.size();
    }
}
