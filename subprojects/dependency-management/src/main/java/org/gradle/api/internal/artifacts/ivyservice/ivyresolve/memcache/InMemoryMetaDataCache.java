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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult.State.Listed;

class InMemoryMetaDataCache {
    private final Map<ModuleVersionSelector, Set<String>> moduleVersionListing = new HashMap<ModuleVersionSelector, Set<String>>();
    private final Map<ModuleComponentIdentifier, CachedModuleVersionResult> metaData = new HashMap<ModuleComponentIdentifier, CachedModuleVersionResult>();

    public boolean supplyModuleVersions(ModuleVersionSelector requested, BuildableModuleVersionListingResolveResult result) {
        Set<String> versions = moduleVersionListing.get(requested);
        if (versions == null) {
            return false;
        }
        result.listed(versions);
        return true;
    }

    public void newModuleVersions(ModuleVersionSelector requested, BuildableModuleVersionListingResolveResult result) {
        if (result.getState() == Listed) {
            moduleVersionListing.put(requested, result.getVersions());
        }
    }

    boolean supplyMetaData(ModuleComponentIdentifier requested, BuildableModuleComponentMetaDataResolveResult result) {
        CachedModuleVersionResult fromCache = metaData.get(requested);
        if (fromCache == null) {
            return false;
        }
        fromCache.supply(result);
        return true;
    }

    void newDependencyResult(ModuleComponentIdentifier requested, BuildableModuleComponentMetaDataResolveResult result) {
        CachedModuleVersionResult cachedResult = new CachedModuleVersionResult(result);
        if (cachedResult.isCacheable()) {
            metaData.put(requested, cachedResult);
        }
    }
}
