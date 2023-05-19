/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.internal.controller;

import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.NextGenBuildCacheService;

import java.io.Closeable;
import java.io.InputStream;
import java.util.Map;

/**
 * Coordinates loading and storing cache entries in batches.
 */
public interface NextGenBuildCacheAccess extends Closeable {
    /**
     * Load the given entries if available.
     *
     * @param entries map of cache keys to load, associated with an optional payload to use during loading.
     * @param handler the handler to call for entries loaded from the cache; passes the corresponding payload, too.
     */
    <T> void load(Map<BuildCacheKey, T> entries, LoadHandler<T> handler);

    /**
     * Store the given entries in the build cache.
     *
     * @param entries map of cache keys to store, associated with an optional payload to use during storing.
     * @param handler the handler to produce the bytes to store for each entry; passes the corresponding payload, too.
     */
    <T> void store(Map<BuildCacheKey, T> entries, StoreHandler<T> handler);

    interface LoadHandler<T> {
        /**
         * Handle the loaded content.
         *
         * @param input the content loaded from the cache.
         * @param payload optional payload belonging to this entry (see {@link #load(Map, LoadHandler)}).
         */
        void handle(InputStream input, T payload);

        void startLoad(BuildCacheKey key);

        void recordLoadHit(BuildCacheKey key, long size);

        void recordLoadMiss(BuildCacheKey key);

        void recordLoadFailure(BuildCacheKey key, Throwable failure);

        void recordUnpackFailure(BuildCacheKey key, Throwable failure);
    }

    interface StoreHandler<T> {
        /**
         * Handle the loaded content.
         *
         * @param payload optional payload belonging to this entry (see {@link #store(Map, StoreHandler)}).
         * @return the writer that can produce the content for this entry.
         */
        NextGenBuildCacheService.NextGenWriter createWriter(T payload);

        void startStore(BuildCacheKey key);

        void recordFinished(BuildCacheKey key, boolean stored);

        void recordStoreFailure(BuildCacheKey key, Throwable failure);

        void recordPackFailure(BuildCacheKey key, Throwable failure);
    }

    abstract class DelegatingLoadHandler<T> implements LoadHandler<T> {
        private final LoadHandler<T> delegate;

        public DelegatingLoadHandler(LoadHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void startLoad(BuildCacheKey key) {
            delegate.startLoad(key);
        }

        @Override
        public void recordLoadHit(BuildCacheKey key, long size) {
            delegate.recordLoadHit(key, size);
        }

        @Override
        public void recordLoadMiss(BuildCacheKey key) {
            delegate.recordLoadMiss(key);
        }

        @Override
        public void recordLoadFailure(BuildCacheKey key, Throwable t) {
            delegate.recordLoadFailure(key, t);
        }

        @Override
        public void recordUnpackFailure(BuildCacheKey key, Throwable failure) {
            delegate.recordUnpackFailure(key, failure);
        }
    }

    abstract class DelegatingStoreHandler<T> implements StoreHandler<T> {
        private final StoreHandler<T> delegate;

        public DelegatingStoreHandler(StoreHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void startStore(BuildCacheKey key) {
            delegate.startStore(key);
        }

        @Override
        public void recordFinished(BuildCacheKey key, boolean stored) {
            delegate.recordFinished(key, stored);
        }

        @Override
        public void recordStoreFailure(BuildCacheKey key, Throwable failure) {
            delegate.recordStoreFailure(key, failure);
        }

        @Override
        public void recordPackFailure(BuildCacheKey key, Throwable failure) {
            delegate.recordPackFailure(key, failure);
        }
    }
}
