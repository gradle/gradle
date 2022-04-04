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

package org.gradle.caching.internal.controller.service;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.util.Optional;
import java.util.function.Function;

public interface RemoteBuildCacheServiceHandle extends Closeable {

    @Nullable
    @VisibleForTesting
    BuildCacheService getService();

    boolean canLoad();

    /**
     * Load the cached entry corresponding to the given cache key to the given target file.
     *
     * If canLoad() returns false, then this method will do nothing and will return empty result.
     */
    Optional<BuildCacheLoadResult> maybeLoad(BuildCacheKey key, File toFile, Function<File, BuildCacheLoadResult> unpackFunction);

    boolean canStore();

    /**
     * Stores the file to the cache.
     *
     * If canStore() returns false, then this method will do nothing and will return false.
     *
     * Returns true if store was completed.
     */
    boolean maybeStore(BuildCacheKey key, File file);

    @Override
    void close();
}
