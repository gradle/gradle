/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache.dynamicversions;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.util.BuildCommencedTimeProvider;

import java.util.Set;

public class ReadOnlyModuleVersionsCache extends DefaultModuleVersionsCache {

    public ReadOnlyModuleVersionsCache(BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingManager artifactCacheLockingManager, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        super(timeProvider, artifactCacheLockingManager, moduleIdentifierFactory);
    }

    @Override
    protected void store(ModuleAtRepositoryKey key, ModuleVersionsCacheEntry entry) {
        operationShouldNotHaveBeenCalled();
    }

    @Override
    public void cacheModuleVersionList(ModuleComponentRepository repository, ModuleIdentifier moduleId, Set<String> listedVersions) {
        throw operationShouldNotHaveBeenCalled();
    }

    private static UnsupportedOperationException operationShouldNotHaveBeenCalled() {
        throw new UnsupportedOperationException("A write operation shouldn't have been called in a read-only cache");
    }
}
