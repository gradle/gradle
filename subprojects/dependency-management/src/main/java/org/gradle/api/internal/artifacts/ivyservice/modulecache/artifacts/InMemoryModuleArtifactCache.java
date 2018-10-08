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
import org.gradle.util.BuildCommencedTimeProvider;

import javax.annotation.Nullable;
import java.io.File;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public class InMemoryModuleArtifactCache implements ModuleArtifactCache {
    private final Map<ArtifactAtRepositoryKey, CachedArtifact> inMemoryCache = Maps.newConcurrentMap();
    private final BuildCommencedTimeProvider timeProvider;

    public InMemoryModuleArtifactCache(BuildCommencedTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    @Override
    public void store(ArtifactAtRepositoryKey key, File artifactFile, BigInteger moduleDescriptorHash) {
        inMemoryCache.put(key, new DefaultCachedArtifact(artifactFile, timeProvider.getCurrentTime(), moduleDescriptorHash));
    }

    @Override
    public void storeMissing(ArtifactAtRepositoryKey key, List<String> attemptedLocations, BigInteger descriptorHash) {
        inMemoryCache.put(key, new DefaultCachedArtifact(attemptedLocations, timeProvider.getCurrentTime(), descriptorHash));
    }

    @Nullable
    @Override
    public CachedArtifact lookup(ArtifactAtRepositoryKey key) {
        return inMemoryCache.get(key);
    }

    @Override
    public void clear(ArtifactAtRepositoryKey key) {
        inMemoryCache.remove(key);
    }
}
