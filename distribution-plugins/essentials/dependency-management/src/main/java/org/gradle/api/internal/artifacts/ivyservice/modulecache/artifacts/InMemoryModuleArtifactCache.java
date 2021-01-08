/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.collect.Maps;
import org.gradle.internal.hash.HashCode;
import org.gradle.util.BuildCommencedTimeProvider;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;

public class InMemoryModuleArtifactCache implements ModuleArtifactCache {
    private final Map<ArtifactAtRepositoryKey, CachedArtifact> inMemoryCache = Maps.newConcurrentMap();
    private final BuildCommencedTimeProvider timeProvider;
    private final ModuleArtifactCache delegate;

    public InMemoryModuleArtifactCache(BuildCommencedTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
        this.delegate = null;
    }

    public InMemoryModuleArtifactCache(BuildCommencedTimeProvider timeProvider, ModuleArtifactCache delegate) {
        this.timeProvider = timeProvider;
        this.delegate = delegate;
    }

    @Override
    public void store(ArtifactAtRepositoryKey key, File artifactFile, HashCode moduleDescriptorHash) {
        inMemoryCache.put(key, new DefaultCachedArtifact(artifactFile, timeProvider.getCurrentTime(), moduleDescriptorHash));
        if (delegate != null) {
            delegate.store(key, artifactFile, moduleDescriptorHash);
        }
    }

    @Override
    public void storeMissing(ArtifactAtRepositoryKey key, List<String> attemptedLocations, HashCode descriptorHash) {
        inMemoryCache.put(key, new DefaultCachedArtifact(attemptedLocations, timeProvider.getCurrentTime(), descriptorHash));
        if (delegate != null) {
            delegate.storeMissing(key, attemptedLocations, descriptorHash);
        }
    }

    @Nullable
    @Override
    public CachedArtifact lookup(ArtifactAtRepositoryKey key) {
        CachedArtifact cachedArtifact = inMemoryCache.get(key);
        if (cachedArtifact == null && delegate != null) {
            cachedArtifact = delegate.lookup(key);
            if (cachedArtifact != null) {
                inMemoryCache.put(key, cachedArtifact);
            }
        }
        return cachedArtifact;
    }

    @Override
    public void clear(ArtifactAtRepositoryKey key) {
        inMemoryCache.remove(key);
        if (delegate != null) {
            delegate.clear(key);
        }
    }
}
