/*
 * Copyright 2017 the original author or authors.
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
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.caching.internal.controller.service.BuildCacheLoadResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * High-level controller for build cache operations; can load and store {@link CacheableEntity}s with a given {@link BuildCacheKey}.
 *
 * <p>Note that some implementations are mutable and may change behavior over the lifetime of a build.</p>
 */
@ServiceScope(Scope.Build.class)
public interface BuildCacheController extends Closeable {

    boolean isEnabled();

    Optional<BuildCacheLoadResult> load(BuildCacheKey cacheKey, CacheableEntity cacheableEntity);

    /**
     * Loads the cache entry from only the local cache.
     * Does not check remote cache.
     */
    default Optional<BuildCacheLoadResult> loadLocally(BuildCacheKey cacheKey, CacheableEntity cacheableEntity) {
        return load(cacheKey, cacheableEntity);
    }

    /**
     * Starts an asynchronous download of the cache entry from the remote cache to a temp file.
     * Does NOT unpack into the workspace. The caller must call {@link #loadFromDownloadedRemoteEntry}
     * to unpack, or delete the temp file if not needed.
     *
     * @return a future that completes with the temp file if the entry was found, or null if not found
     */
    default CompletableFuture<@Nullable File> downloadRemoteAsync(BuildCacheKey cacheKey) {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Unpacks a previously downloaded cache entry from a temp file into the entity's workspace
     * and stores it in the local cache.
     *
     * @param downloadedFile the temp file containing the downloaded cache entry
     * @return the load result after unpacking
     */
    default Optional<BuildCacheLoadResult> loadFromDownloadedRemoteEntry(BuildCacheKey cacheKey, CacheableEntity entity, File downloadedFile) {
        return Optional.empty();
    }

    void store(BuildCacheKey cacheKey, CacheableEntity entity, Map<String, FileSystemSnapshot> snapshots, Duration executionTime);
}
