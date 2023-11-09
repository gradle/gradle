/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.changedetection.state;

import org.gradle.cache.FileLockManager;
import org.gradle.cache.PersistentCache;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.cache.scopes.ScopedCacheBuilderFactory;
import org.gradle.internal.execution.history.ExecutionHistoryCacheAccess;

import java.io.Closeable;

public class DefaultExecutionHistoryCacheAccess implements ExecutionHistoryCacheAccess, Closeable {
    private final PersistentCache cache;

    public DefaultExecutionHistoryCacheAccess(ScopedCacheBuilderFactory cacheBuilderFactory) {
        this.cache = cacheBuilderFactory
            .createCacheBuilder("executionHistory")
            .withDisplayName("execution history cache")
            .withLockOptions(new LockOptionsBuilder(FileLockManager.LockMode.OnDemand)) // Lock on demand
            .open();
    }

    @Override
    public PersistentCache get() {
        return cache;
    }

    @Override
    public void close() {
        cache.close();
    }
}
