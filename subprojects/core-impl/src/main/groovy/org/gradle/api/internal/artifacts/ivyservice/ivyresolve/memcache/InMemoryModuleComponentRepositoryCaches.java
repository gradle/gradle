/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.memcache;

public class InMemoryModuleComponentRepositoryCaches {
    public final InMemoryMetaDataCache localMetaDataCache;
    public final InMemoryMetaDataCache remoteMetaDataCache;
    public final InMemoryArtifactsCache localArtifactsCache;
    public final InMemoryArtifactsCache remoteArtifactsCache;
    public final InMemoryCacheStats stats;

    public InMemoryModuleComponentRepositoryCaches(InMemoryCacheStats stats) {
        this(new InMemoryArtifactsCache(stats),
                new InMemoryArtifactsCache(stats),
                new InMemoryMetaDataCache(stats),
                new InMemoryMetaDataCache(stats),
                stats);
    }

    protected InMemoryModuleComponentRepositoryCaches(InMemoryArtifactsCache localArtifactsCache, InMemoryArtifactsCache remoteArtifactsCache,
                                                      InMemoryMetaDataCache localMetaDataCache, InMemoryMetaDataCache remoteMetaDataCache,
                                                      InMemoryCacheStats stats) {
        this.localArtifactsCache = localArtifactsCache;
        this.remoteArtifactsCache = remoteArtifactsCache;
        this.localMetaDataCache = localMetaDataCache;
        this.remoteMetaDataCache = remoteMetaDataCache;
        this.stats = stats;
    }
}
