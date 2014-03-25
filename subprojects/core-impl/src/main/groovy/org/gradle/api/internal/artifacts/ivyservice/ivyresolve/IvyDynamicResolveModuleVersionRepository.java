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

import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.metadata.*;

import java.util.ArrayList;
import java.util.List;

public class IvyDynamicResolveModuleVersionRepository implements LocalAwareModuleVersionRepository {
    private final LocalAwareModuleVersionRepository repository;

    public IvyDynamicResolveModuleVersionRepository(LocalAwareModuleVersionRepository repository) {
        this.repository = repository;
    }

    public String getId() {
        return repository.getId();
    }

    public String getName() {
        return repository.getName();
    }

    public void localListModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        repository.localListModuleVersions(dependency, result);
    }

    public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        repository.listModuleVersions(dependency, result);
    }

    public void getLocalDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        repository.getLocalDependency(dependency, result);
        if (result.getState() == BuildableModuleVersionMetaDataResolveResult.State.Resolved) {
            transformDependencies(result);
        }
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        repository.getDependency(dependency, result);
        if (result.getState() == BuildableModuleVersionMetaDataResolveResult.State.Resolved) {
            transformDependencies(result);
        }
    }

    private void transformDependencies(BuildableModuleVersionMetaDataResolveResult result) {
        MutableModuleVersionMetaData metaData = result.getMetaData();
        List<DependencyMetaData> transformed = new ArrayList<DependencyMetaData>();
        for (DependencyMetaData dependency : metaData.getDependencies()) {
            transformed.add(dependency.withRequestedVersion(dependency.getDescriptor().getDynamicConstraintDependencyRevisionId().getRevision()));
        }
        metaData.setDependencies(transformed);
    }

    public void resolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        repository.resolveModuleArtifacts(component, context, result);
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        repository.resolveArtifact(artifact, moduleSource, result);
    }

}
