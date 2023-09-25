/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.cache.internal;

import org.gradle.cache.UnitOfWorkParticipant;

import javax.annotation.Nullable;
import java.util.function.Function;

/**
 * An indexed cache that may perform updates asynchronously.
 */
public interface MultiProcessSafeAsyncPersistentIndexedCache<K, V> extends UnitOfWorkParticipant {
    /**
     * Fetches the given entry, blocking until the result is available.
     */
    @Nullable
    V get(K key);

    /**
     * Fetches the given entry, producing if necessary, blocking until the result is available. This method may or may not block until any updates have completed and will invoke the given completion action when the operation is complete.
     */
    V get(K key, Function<? super K, ? extends V> producer, Runnable completion);

    /**
     * Submits an update to be applied later. This method may or may not block, and will invoke the given completion action when the operation is complete.
     */
    void putLater(K key, V value, Runnable completion);

    /**
     * Submits a removal to be applied later. This method may or may not block, and will invoke the given completion action when the operation is complete.
     */
    void removeLater(K key, Runnable completion);
}
