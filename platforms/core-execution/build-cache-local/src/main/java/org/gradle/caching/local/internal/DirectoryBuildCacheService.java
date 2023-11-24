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
import org.gradle.api.NonNullApi;
import org.gradle.cache.PersistentCache;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.internal.DefaultBuildCacheKey;
import org.gradle.internal.file.FileAccessTracker;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.local.PathKeyFileStore;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

@NonNullApi
public class DirectoryBuildCacheService implements LocalBuildCacheService, BuildCacheService {

    private final DirectoryBuildCache cache;

    public DirectoryBuildCacheService(PathKeyFileStore fileStore, PersistentCache persistentCache, BuildCacheTempFileStore tempFileStore, FileAccessTracker fileAccessTracker, String failedFileSuffix) {
        this.cache = new DirectoryBuildCache(fileStore, persistentCache, tempFileStore, fileAccessTracker, failedFileSuffix);
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        return cache.load(((DefaultBuildCacheKey) key).getHashCodeInternal(), reader::readFrom);
    }

    @Override
    public void loadLocally(BuildCacheKey key, Action<? super File> reader) {
        cache.loadLocally(((DefaultBuildCacheKey) key).getHashCodeInternal(), reader::execute);
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter result) throws BuildCacheException {
        cache.store(((DefaultBuildCacheKey) key).getHashCodeInternal(), result::writeTo);
    }

    @Override
    public void storeLocally(BuildCacheKey key, File file) {
        cache.storeLocally(((DefaultBuildCacheKey) key).getHashCodeInternal(), file);
    }

    @Override
    public void withTempFile(HashCode key, Consumer<? super File> action) {
        cache.withTempFile(key, action);
    }

    @Override
    public void close() throws IOException {
        cache.close();
    }
}
