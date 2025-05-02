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

import com.google.common.collect.MapMaker;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BaseModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.component.external.model.ExternalModuleComponentGraphResolveState;
import org.gradle.internal.concurrent.Stoppable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the dependency metadata (descriptors, artifact files) in memory.
 */
public class ResolvedArtifactCaches implements Stoppable {

    private final static Logger LOG = Logging.getLogger(ResolvedArtifactCaches.class);

    private final Map<String, Map<ComponentArtifactIdentifier, ResolvableArtifact>> cachePerRepo = new MapMaker().makeMap();
    private final Map<String, Map<ComponentArtifactIdentifier, ResolvableArtifact>> cachePerRepoWithVerification = new MapMaker().makeMap();

    /**
     * For a remote repository, the only thing required is a resolved artifact cache.
     * The rest of the in-memory caching is handled by the CachingModuleComponentRepository.
     */
    public ModuleComponentRepository<ExternalModuleComponentGraphResolveState> provideResolvedArtifactCache(ModuleComponentRepository<ExternalModuleComponentGraphResolveState> input, boolean withVerification) {
        Map<ComponentArtifactIdentifier, ResolvableArtifact> caches = getResolvedArtifactCache(withVerification ? cachePerRepoWithVerification : cachePerRepo, input);
        return new ResolvedArtifactCacheProvidingModuleComponentRepository(caches, input);
    }

    private Map<ComponentArtifactIdentifier, ResolvableArtifact> getResolvedArtifactCache(Map<String, Map<ComponentArtifactIdentifier, ResolvableArtifact>> cache, ModuleComponentRepository<ExternalModuleComponentGraphResolveState> input) {
        Map<ComponentArtifactIdentifier, ResolvableArtifact> resolvedArtifactCache = cache.get(input.getId());
        if (resolvedArtifactCache == null) {
            LOG.debug("Creating new in-memory cache for repo '{}' [{}].", input.getName(), input.getId());
            resolvedArtifactCache = new ConcurrentHashMap<>();
            cache.put(input.getId(), resolvedArtifactCache);
        } else {
            LOG.debug("Reusing in-memory cache for repo '{}' [{}].", input.getName(), input.getId());
        }
        return resolvedArtifactCache;
    }

    @Override
    public void stop() {
        cachePerRepo.clear();
        cachePerRepoWithVerification.clear();
    }

    private static class ResolvedArtifactCacheProvidingModuleComponentRepository extends BaseModuleComponentRepository<ExternalModuleComponentGraphResolveState> {

        private final Map<ComponentArtifactIdentifier, ResolvableArtifact> resolvedArtifactCache;

        public ResolvedArtifactCacheProvidingModuleComponentRepository(Map<ComponentArtifactIdentifier, ResolvableArtifact> resolvedArtifactsCache, ModuleComponentRepository<ExternalModuleComponentGraphResolveState> delegate) {
            super(delegate);
            this.resolvedArtifactCache = resolvedArtifactsCache;
        }

        @Override
        public Map<ComponentArtifactIdentifier, ResolvableArtifact> getArtifactCache() {
            return resolvedArtifactCache;
        }
    }
}
