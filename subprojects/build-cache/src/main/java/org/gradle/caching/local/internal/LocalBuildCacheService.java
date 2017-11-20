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

package org.gradle.caching.local.internal;

import org.gradle.api.Action;
import org.gradle.caching.BuildCacheKey;

import java.io.Closeable;
import java.io.File;

/**
 * A build cache service that is capable of handling local files directly. The direct access
 * allows more optimized implementations than the more general {@link org.gradle.caching.BuildCacheService}
 * interface.
 */
public interface LocalBuildCacheService extends BuildCacheTempFileStore, Closeable {

    /**
     * Loads a cache artifact from a local file store. If a result is found the {@code reader} is executed.
     */
    void loadLocally(BuildCacheKey key, Action<? super File> reader);

    /**
     * Store the given file in the local file store as a cache artifact.
     */
    void storeLocally(BuildCacheKey key, File file);

    @Override
    void close();
}
