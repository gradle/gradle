/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolvedModuleVersion;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.dynamicversions.ModuleResolutionCache;
import org.gradle.api.internal.artifacts.ivyservice.modulecache.ModuleDescriptorCache;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RefreshWhenMissingInAllRepositoriesCachePolicy implements CachePolicy {
    private final ModuleResolutionCache moduleResolutionCache;
    private final ModuleDescriptorCache moduleDescriptorCache;

    //We use List here instead of Set to make the behaviour of this CachePolicy Implementation more predictable.
    private List<ModuleVersionRepository> repositories = new ArrayList<ModuleVersionRepository>();

    public RefreshWhenMissingInAllRepositoriesCachePolicy(ModuleResolutionCache moduleResolutionCache, ModuleDescriptorCache moduleDescriptorCache) {
        this.moduleResolutionCache = moduleResolutionCache;
        this.moduleDescriptorCache = moduleDescriptorCache;
    }

    public boolean mustRefreshDynamicVersion(ModuleVersionSelector selector, ModuleVersionIdentifier moduleId, long ageMillis) {
        return false;
    }

    public boolean mustRefreshModule(ModuleVersionIdentifier moduleVersionId, ResolvedModuleVersion resolvedModuleVersion, ModuleRevisionId moduleRevisionId, long ageMillis) {
        return isGloballyNotFound(moduleRevisionId);
    }

    private boolean isGloballyNotFound(ModuleRevisionId moduleRevisionId) {
        if (moduleRevisionId == null) {
            return false;
        }
        for (ModuleVersionRepository repository : repositories) {
            if (isResolvedDynamicModule(moduleRevisionId, repository)) {
                return false;
            }
            final ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptor = moduleDescriptorCache.getCachedModuleDescriptor(repository, moduleRevisionId);
            if (cachedModuleDescriptor != null && !cachedModuleDescriptor.isMissing()) {
                return false;
            }
        }
        return true;
    }

    private boolean isResolvedDynamicModule(ModuleRevisionId moduleRevisionId, ModuleVersionRepository repository) {
        final ModuleResolutionCache.CachedModuleResolution cachedModuleResolution = moduleResolutionCache.getCachedModuleResolution(repository, moduleRevisionId);
        if (cachedModuleResolution != null) {
            final ModuleDescriptorCache.CachedModuleDescriptor cachedModuleDescriptor = moduleDescriptorCache.getCachedModuleDescriptor(repository, cachedModuleResolution.getResolvedVersion());
            if (cachedModuleDescriptor != null && !cachedModuleDescriptor.isMissing()) {
                return true;
            }
        }
        return false;
    }

    public boolean mustRefreshChangingModule(ModuleVersionIdentifier moduleVersionId, ResolvedModuleVersion resolvedModuleVersion, long ageMillis) {
        return false;
    }

    public boolean mustRefreshArtifact(ArtifactIdentifier artifactIdentifier, File cachedArtifactFile, long ageMillis) {
        return false;
    }

    public void registerRepository(ModuleVersionRepository moduleVersionRepository) {
        this.repositories.add(moduleVersionRepository);
    }


}
