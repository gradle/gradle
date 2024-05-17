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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts;

import org.gradle.internal.hash.HashCode;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class TwoStageModuleArtifactCache implements ModuleArtifactCache {
    private final ModuleArtifactCache readOnlyCache;
    private final ModuleArtifactCache writableCache;
    private final Path readOnlyCachePath;

    public TwoStageModuleArtifactCache(Path readOnlyCachePath, ModuleArtifactCache readOnlyCache, ModuleArtifactCache writableCache) {
        this.readOnlyCachePath = readOnlyCachePath;
        this.readOnlyCache = readOnlyCache;
        this.writableCache = writableCache;
    }

    @Override
    public void store(ArtifactAtRepositoryKey key, File artifactFile, HashCode moduleDescriptorHash) {
        if (artifactFile.toPath().startsWith(readOnlyCachePath)) {
            // skip writing because the file comes from the RO cache
            return;
        }
        writableCache.store(key, artifactFile,  moduleDescriptorHash);
    }

    @Override
    public void storeMissing(ArtifactAtRepositoryKey key, List<String> attemptedLocations, HashCode descriptorHash) {
        writableCache.storeMissing(key, attemptedLocations, descriptorHash);
    }

    @Nullable
    @Override
    public CachedArtifact lookup(ArtifactAtRepositoryKey key) {
        CachedArtifact lookup = writableCache.lookup(key);
        if (lookup != null) {
            return lookup;
        }
        return readOnlyCache.lookup(key);
    }

    @Override
    public void clear(ArtifactAtRepositoryKey key) {
        writableCache.clear(key);
    }
}
