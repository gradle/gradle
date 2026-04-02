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

    /**
     * Whether this cache supports concurrent reads from multiple threads without
     * external synchronization. When true, {@code get()} may be called from any
     * thread while another thread is performing {@code put()} or {@code flush()}.
     *
     * <p>Implementations backed by a write-behind {@link java.util.concurrent.ConcurrentHashMap}
     * and a thread-safe read path (e.g. MVStore's MVCC pages) return {@code true}.
     * Implementations with non-thread-safe internals (e.g. BTree's CachingBlockStore)
     * return {@code false}.
     */
    default boolean supportsConcurrentReads() {
        return false;
    }

    void put(K key, V value);

    void remove(K key);

    /**
     * Flushes any buffered writes to the backing store without closing it.
     * Called by {@code finishWork()} before file lock release to ensure durability
     * while keeping the store open for reuse on the next lock acquire.
     */
    void flush();

    void close();

    boolean isOpen();

    void reset();

    void clear();

    void verify();
}
