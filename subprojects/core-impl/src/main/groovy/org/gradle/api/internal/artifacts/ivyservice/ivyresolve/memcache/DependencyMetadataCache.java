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

import org.gradle.api.artifacts.ArtifactIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
* By Szczepan Faber on 4/19/13
*/
class DependencyMetadataCache {
    private final Map<ModuleVersionSelector, CachedModuleVersionResult> localMetaData = new HashMap<ModuleVersionSelector, CachedModuleVersionResult>();
    private final Map<ModuleVersionSelector, CachedModuleVersionResult> metaData = new HashMap<ModuleVersionSelector, CachedModuleVersionResult>();
    private final Map<ArtifactIdentifier, File> artifacts = new HashMap<ArtifactIdentifier, File>();
    private DependencyMetadataCacheStats stats;

    DependencyMetadataCache(DependencyMetadataCacheStats stats) {
        this.stats = stats;
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

    public boolean supplyArtifact(ArtifactIdentifier id, BuildableArtifactResolveResult result) {
        File fromCache = artifacts.get(id);
        if (fromCache != null) {
            result.resolved(fromCache);
            stats.artifactsServed++;
            return true;
        }
        return false;
    }

    public void newArtifact(ArtifactIdentifier id, BuildableArtifactResolveResult result) {
        artifacts.put(id, result.getFile());
    }
}