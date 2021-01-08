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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.hash.HashCode;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.Collection;

public class ReadOnlyModuleArtifactsCache extends DefaultModuleArtifactsCache {
    public ReadOnlyModuleArtifactsCache(BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingManager artifactCacheLockingManager) {
        super(timeProvider, artifactCacheLockingManager);
    }

    @Override
    protected void store(ArtifactsAtRepositoryKey key, ModuleArtifactsCacheEntry entry) {
        operationShouldNotHaveBeenCalled();
    }

    @Override
    public CachedArtifacts cacheArtifacts(ModuleComponentRepository repository, ComponentIdentifier componentId, String context, HashCode descriptorHash, Collection<? extends ComponentArtifactMetadata> artifacts) {
        return operationShouldNotHaveBeenCalled();
    }

    private static <T> T operationShouldNotHaveBeenCalled() {
        throw new UnsupportedOperationException("A write operation shouldn't have been called in a read-only cache");
    }

}
