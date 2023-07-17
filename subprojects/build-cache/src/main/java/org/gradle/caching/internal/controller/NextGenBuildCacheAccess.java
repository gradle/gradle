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
import org.gradle.caching.internal.operations.BuildCacheRemoteLoadBuildOperationType;
import org.gradle.caching.internal.operations.BuildCacheRemoteStoreBuildOperationType;

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

        /**
         * Starts the legacy {@link BuildCacheRemoteLoadBuildOperationType} build operation when the first download starts.
         */
        void ensureLoadOperationStarted(BuildCacheKey key);

        /**
         * Record that the load operation was successful in loading a result.
         * @param key the cache key of the finished entry.
         * @param size the number of bytes that has been loaded.
         */
        void recordLoadHit(BuildCacheKey key, long size);

        /**
         * Record that the load operation finished without error, but could not find the given entry.
         * @param key the cache key of the finished entry.
         */
        void recordLoadMiss(BuildCacheKey key);

        /**
         * Record that the load operation failed.
         * @param key the cache key of the finished entry.
         * @param failure the error that occurred.
         */
        void recordLoadFailure(BuildCacheKey key, Throwable failure);

        /**
         * Record that the unpack operation failed.
         * @param key the cache key of the finished entry.
         * @param failure the error that occurred.
         */
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

        /**
         * Starts the legacy {@link BuildCacheRemoteStoreBuildOperationType} build operation when the first upload starts.
         */
        void ensureStoreOperationStarted(BuildCacheKey key);

        /**
         * Record that the store operation has finished successfully.
         * @param key the cache key of the finished entry.
         * @param stored whether anything was uploadedd to the server.
         */
        void recordStoreFinished(BuildCacheKey key, boolean stored);

        /**
         * Record that the store operation failed.
         * @param key the cache key of the finished entry.
         * @param failure the error that occurred.
         */
        void recordStoreFailure(BuildCacheKey key, Throwable failure);

        /**
         * Record that the pack operation failed.
         * @param key the cache key of the finished entry.
         * @param failure the error that occurred.
         */
        void recordPackFailure(BuildCacheKey key, Throwable failure);
    }

    abstract class DelegatingLoadHandler<T> implements LoadHandler<T> {
        private final LoadHandler<T> delegate;

        public DelegatingLoadHandler(LoadHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void ensureLoadOperationStarted(BuildCacheKey key) {
            delegate.ensureLoadOperationStarted(key);
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
        public void ensureStoreOperationStarted(BuildCacheKey key) {
            delegate.ensureStoreOperationStarted(key);
        }

        @Override
        public void recordStoreFinished(BuildCacheKey key, boolean stored) {
            delegate.recordStoreFinished(key, stored);
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
