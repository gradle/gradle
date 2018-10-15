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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import com.google.common.collect.Maps;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.AbstractArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.InMemoryModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.InMemoryModuleArtifactsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts.ModuleArtifactCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.AbstractModuleVersionsCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions.InMemoryModuleVersionsCache;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.Map;

public class ModuleRepositoryCacheProvider {
    private final BuildCommencedTimeProvider timeProvider;
    private final ModuleRepositoryCaches undecoratedCaches;
    private final Map<Object, CachesHolder> caches = Maps.newHashMap();
    private final ResolvedArtifactCaches resolvedArtifactCaches = new ResolvedArtifactCaches();

    // This is a performance optimization: the artifact cache is safely shareable
    // so we avoid creating one instance per project
    private InMemoryModuleArtifactCache sharedModuleArtifactCache;
    private InMemoryModuleArtifactCache sharedInMemoryOnlyArtifactCache;

    public ModuleRepositoryCacheProvider(BuildCommencedTimeProvider timeProvider, ModuleRepositoryCaches undecoratedCaches) {
        this.timeProvider = timeProvider;
        this.undecoratedCaches = undecoratedCaches;
    }

    public ModuleRepositoryCaches getCaches(Object scope) {
        return getOrCreate(scope).caches;
    }

    public ModuleRepositoryCaches getInMemoryOnlyCaches(Object scope) {
        return getOrCreate(scope).inMemoryOnlyCaches;
    }

    public synchronized CachesHolder getOrCreate(Object scope) {
        CachesHolder cachesHolder = caches.get(scope);
        if (cachesHolder == null) {
            cachesHolder = createHolder();
            caches.put(scope, cachesHolder);
        }
        return cachesHolder;
    }

    private CachesHolder createHolder() {
        ModuleRepositoryCaches decorated;
        if (undecoratedCaches.moduleVersionsCache instanceof AbstractModuleVersionsCache) {
            AbstractModuleVersionsCache moduleVersionsCache = (AbstractModuleVersionsCache) undecoratedCaches.moduleVersionsCache;
            AbstractModuleMetadataCache moduleMetadataCache = (AbstractModuleMetadataCache) undecoratedCaches.moduleMetadataCache;
            AbstractArtifactsCache moduleArtifactsCache = (AbstractArtifactsCache) undecoratedCaches.moduleArtifactsCache;
            ModuleArtifactCache moduleArtifactCache = undecoratedCaches.moduleArtifactCache;
            if (sharedModuleArtifactCache == null) {
                sharedModuleArtifactCache = new InMemoryModuleArtifactCache(timeProvider, moduleArtifactCache);
            }
            decorated = new ModuleRepositoryCaches(
                new InMemoryModuleVersionsCache(timeProvider, moduleVersionsCache),
                new InMemoryModuleMetadataCache(timeProvider, moduleMetadataCache),
                new InMemoryModuleArtifactsCache(timeProvider, moduleArtifactsCache),
                sharedModuleArtifactCache
            );
        } else {
            // this happens from tests
            decorated = undecoratedCaches;
        }
        if (sharedInMemoryOnlyArtifactCache == null) {
            sharedInMemoryOnlyArtifactCache = new InMemoryModuleArtifactCache(timeProvider);
        }
        ModuleRepositoryCaches memoryOnly = new ModuleRepositoryCaches(
            new InMemoryModuleVersionsCache(timeProvider),
            new InMemoryModuleMetadataCache(timeProvider),
            new InMemoryModuleArtifactsCache(timeProvider),
            sharedInMemoryOnlyArtifactCache
        );
        return new CachesHolder(decorated, memoryOnly);
    }

    public ResolvedArtifactCaches getResolvedArtifactCaches() {
        return resolvedArtifactCaches;
    }

    private static class CachesHolder {
        private final ModuleRepositoryCaches caches;
        private final ModuleRepositoryCaches inMemoryOnlyCaches;

        private CachesHolder(ModuleRepositoryCaches caches, ModuleRepositoryCaches inMemoryOnlyCaches) {
            this.caches = caches;
            this.inMemoryOnlyCaches = inMemoryOnlyCaches;
        }

    }
}
