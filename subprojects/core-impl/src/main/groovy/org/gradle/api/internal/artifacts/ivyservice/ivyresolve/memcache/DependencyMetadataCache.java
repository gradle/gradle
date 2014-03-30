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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.BuildableModuleVersionMetaDataResolveResult;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactIdentifier;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

class DependencyMetadataCache {
    private final Map<ModuleComponentIdentifier, CachedModuleVersionResult> localMetaData = new HashMap<ModuleComponentIdentifier, CachedModuleVersionResult>();
    private final Map<ModuleComponentIdentifier, CachedModuleVersionResult> metaData = new HashMap<ModuleComponentIdentifier, CachedModuleVersionResult>();
    private final Map<ComponentArtifactIdentifier, File> artifacts = new HashMap<ComponentArtifactIdentifier, File>();
    private DependencyMetadataCacheStats stats;

    DependencyMetadataCache(DependencyMetadataCacheStats stats) {
        this.stats = stats;
    }

    boolean supplyLocalMetaData(ModuleComponentIdentifier requested, BuildableModuleVersionMetaDataResolveResult result) {
        return supply(requested, result, localMetaData, stats);
    }

    boolean supplyMetaData(ModuleComponentIdentifier requested, BuildableModuleVersionMetaDataResolveResult result) {
        return supply(requested, result, metaData, stats);
    }

    private static boolean supply(ModuleComponentIdentifier requested, BuildableModuleVersionMetaDataResolveResult result, Map<ModuleComponentIdentifier, CachedModuleVersionResult> map, DependencyMetadataCacheStats stats) {
        CachedModuleVersionResult fromCache = map.get(requested);
        if (fromCache == null) {
            return false;
        }
        fromCache.supply(result);
        stats.metadataServed++;
        return true;
    }

    void newLocalDependencyResult(ModuleComponentIdentifier requested, BuildableModuleVersionMetaDataResolveResult result) {
        newResult(requested, result, localMetaData);
    }

    void newDependencyResult(ModuleComponentIdentifier requested, BuildableModuleVersionMetaDataResolveResult result) {
        newResult(requested, result, metaData);
    }

    private static void newResult(ModuleComponentIdentifier requested, BuildableModuleVersionMetaDataResolveResult result, Map<ModuleComponentIdentifier, CachedModuleVersionResult> map) {
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
        artifacts.put(id, result.getFile());
    }
}