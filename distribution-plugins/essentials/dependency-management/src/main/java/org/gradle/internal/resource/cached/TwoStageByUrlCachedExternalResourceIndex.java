/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.internal.resource.cached;

import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Path;

public class TwoStageByUrlCachedExternalResourceIndex implements CachedExternalResourceIndex<String> {
    private final Path readOnlyCachePath;
    private final CachedExternalResourceIndex<String> readOnlyCache;
    private final CachedExternalResourceIndex<String> writableCache;

    public TwoStageByUrlCachedExternalResourceIndex(Path readOnlyCachePath, CachedExternalResourceIndex<String> readOnlyCache, CachedExternalResourceIndex<String> writableCache) {
        this.readOnlyCachePath = readOnlyCachePath;
        this.readOnlyCache = readOnlyCache;
        this.writableCache = writableCache;
    }

    @Override
    public void store(String key, File artifactFile, @Nullable ExternalResourceMetaData metaData) {
        if (artifactFile.toPath().startsWith(readOnlyCachePath)) {
            // skip writing because the file comes from the RO cache
            return;
        }
        writableCache.store(key, artifactFile, metaData);
    }

    @Override
    public void storeMissing(String key) {
        writableCache.storeMissing(key);
    }

    @Nullable
    @Override
    public CachedExternalResource lookup(String key) {
        CachedExternalResource lookup = writableCache.lookup(key);
        if (lookup != null) {
            return lookup;
        }
        return readOnlyCache.lookup(key);
    }

    @Override
    public void clear(String key) {
        writableCache.clear(key);
    }
}
