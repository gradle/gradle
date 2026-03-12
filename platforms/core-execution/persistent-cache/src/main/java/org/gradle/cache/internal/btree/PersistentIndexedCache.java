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
package org.gradle.cache.internal.btree;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Internal interface for persistent indexed cache implementations.
 *
 * <p>This is not a public API. It defines the contract that backing store
 * implementations must satisfy for use by {@code DefaultMultiProcessSafeIndexedCache}.</p>
 */
@NullMarked
public interface PersistentIndexedCache<K, V> {

    @Nullable
    V get(K key);

    void put(K key, V value);

    void remove(K key);

    void close();

    boolean isOpen();

    void reset();

    void clear();

    void verify();
}
