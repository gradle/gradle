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
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionSelectionResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionListing;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifier;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionSelectionResolveResult.State.*;

class DependencyMetadataCache {
    private final Map<ModuleVersionSelector, ModuleVersionListing> localModuleVersionListing = new HashMap<ModuleVersionSelector, ModuleVersionListing>();
    private final Map<ModuleVersionSelector, ModuleVersionListing> moduleVersionListing = new HashMap<ModuleVersionSelector, ModuleVersionListing>();
    private final Map<ModuleVersionSelector, CachedModuleVersionResult> localMetaData = new HashMap<ModuleVersionSelector, CachedModuleVersionResult>();
    private final Map<ModuleVersionSelector, CachedModuleVersionResult> metaData = new HashMap<ModuleVersionSelector, CachedModuleVersionResult>();
    private final Map<ComponentArtifactIdentifier, File> artifacts = new HashMap<ComponentArtifactIdentifier, File>();
    private DependencyMetadataCacheStats stats;

    DependencyMetadataCache(DependencyMetadataCacheStats stats) {
        this.stats = stats;
    }

    public boolean supplyLocalModuleVersions(ModuleVersionSelector requested, BuildableModuleVersionSelectionResolveResult result) {
        return supply(requested, result, localModuleVersionListing);
    }

    public void newLocalModuleVersions(ModuleVersionSelector requested, BuildableModuleVersionSelectionResolveResult result) {
        newResult(requested, result, localModuleVersionListing);
    }

    public boolean supplyModuleVersions(ModuleVersionSelector requested, BuildableModuleVersionSelectionResolveResult result) {
        return supply(requested, result, moduleVersionListing);
    }

    public void newModuleVersions(ModuleVersionSelector requested, BuildableModuleVersionSelectionResolveResult result) {
        newResult(requested, result, moduleVersionListing);
    }

    private boolean supply(ModuleVersionSelector requested, BuildableModuleVersionSelectionResolveResult result, Map<ModuleVersionSelector, ModuleVersionListing> map) {
        ModuleVersionListing moduleVersionListing = map.get(requested);
        if (moduleVersionListing == null) {
            return false;
        }
        result.listed(moduleVersionListing);
        return true;
    }

    private void newResult(ModuleVersionSelector requested, BuildableModuleVersionSelectionResolveResult result, Map<ModuleVersionSelector, ModuleVersionListing> map) {
        if (result.getState() == Listed || result.getState() == ProbablyListed) {
            map.put(requested, result.getVersions());
        }
    }

    boolean supplyLocalMetaData(ModuleVersionSelector requested, BuildableModuleVersionMetaDataResolveResult result) {
        return supply(requested, result, localMetaData, stats);
    }

    boolean supplyMetaData(ModuleVersionSelector requested, BuildableModuleVersionMetaDataResolveResult result) {
        return supply(requested, result, metaData, stats);
    }

    private static boolean supply(ModuleVersionSelector requested, BuildableModuleVersionMetaDataResolveResult result, Map<ModuleVersionSelector, CachedModuleVersionResult> map, DependencyMetadataCacheStats stats) {
        CachedModuleVersionResult fromCache = map.get(requested);
        if (fromCache == null) {
            return false;
        }
        fromCache.supply(result);
        stats.metadataServed++;
        return true;
    }

    void newLocalDependencyResult(ModuleVersionSelector requested, BuildableModuleVersionMetaDataResolveResult result) {
        newResult(requested, result, localMetaData);
    }

    void newDependencyResult(ModuleVersionSelector requested, BuildableModuleVersionMetaDataResolveResult result) {
        newResult(requested, result, metaData);
    }

    private static void newResult(ModuleVersionSelector requested, BuildableModuleVersionMetaDataResolveResult result, Map<ModuleVersionSelector, CachedModuleVersionResult> map) {
        CachedModuleVersionResult cachedResult = new CachedModuleVersionResult(result);
        if (cachedResult.isCacheable()) {
            map.put(requested, cachedResult);
        }
    }

    public boolean supplyArtifact(ComponentArtifactIdentifier id, BuildableArtifactResolveResult result) {
        File fromCache = artifacts.get(id);
        if (fromCache != null) {
            result.resolved(fromCache);
            stats.artifactsServed++;
            return true;
        }
        return false;
    }

    public void newArtifact(ComponentArtifactIdentifier id, BuildableArtifactResolveResult result) {
        if (result.getFailure() == null) {
            artifacts.put(id, result.getFile());
        }
    }
}