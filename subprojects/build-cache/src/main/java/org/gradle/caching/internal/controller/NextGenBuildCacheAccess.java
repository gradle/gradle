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

public interface NextGenBuildCacheAccess extends Closeable {
    <T> void load(Map<BuildCacheKey, T> entries, LoadHandler<T> handler);

    <T> void store(Map<BuildCacheKey, T> entries, StoreHandler<T> handler);

    interface LoadHandler<T> {
        void handle(InputStream input, T payload);
        void startRemoteDownload(BuildCacheKey key);
        void recordHit(BuildCacheKey key, long size);
        void recordMiss(BuildCacheKey key);
        void recordFailure(BuildCacheKey key, Throwable failure);
    }

    interface StoreHandler<T> {
        NextGenBuildCacheService.NextGenWriter createWriter(T payload);
        void startRemoteUpload(BuildCacheKey key);
        void recordFinished(BuildCacheKey key, boolean stored);
        void recordFailure(BuildCacheKey key, Throwable failure);
    }

    abstract class DelegatingLoadHandler<T> implements LoadHandler<T> {
        private final LoadHandler<T> delegate;

        public DelegatingLoadHandler(LoadHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void startRemoteDownload(BuildCacheKey key) {
            delegate.startRemoteDownload(key);
        }

        @Override
        public void recordHit(BuildCacheKey key, long size) {
            delegate.recordHit(key, size);
        }

        @Override
        public void recordMiss(BuildCacheKey key) {
            delegate.recordMiss(key);
        }

        @Override
        public void recordFailure(BuildCacheKey key, Throwable t) {
            delegate.recordFailure(key, t);
        }
    }

    abstract class DelegatingStoreHandler<T> implements StoreHandler<T> {
        private final StoreHandler<T> delegate;

        public DelegatingStoreHandler(StoreHandler<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void startRemoteUpload(BuildCacheKey key) {
            delegate.startRemoteUpload(key);
        }

        @Override
        public void recordFinished(BuildCacheKey key, boolean stored) {
            delegate.recordFinished(key, stored);
        }

        @Override
        public void recordFailure(BuildCacheKey key, Throwable failure) {
            delegate.recordFailure(key, failure);
        }
    }
}
