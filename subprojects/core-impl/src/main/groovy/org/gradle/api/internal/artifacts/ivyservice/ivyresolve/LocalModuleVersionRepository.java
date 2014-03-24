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

import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;

public class LocalModuleVersionRepository implements LocalAwareModuleVersionRepository {
    private final ModuleVersionRepository delegate;
    private final ModuleMetadataProcessor processor;

    public LocalModuleVersionRepository(ModuleVersionRepository delegate, ModuleMetadataProcessor processor) {
        this.delegate = delegate;
        this.processor = processor;
    }

    public void localListModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        delegate.listModuleVersions(dependency, result);
    }

    public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
    }

    public void getLocalDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        delegate.getDependency(dependency, result);
        if (result.getState() == BuildableModuleVersionMetaDataResolveResult.State.Resolved) {
            processor.process(result.getMetaData());
        }
    }

    public void getDependency(DependencyMetaData dependency, BuildableModuleVersionMetaDataResolveResult result) {
        result.missing();
    }

    public void resolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        delegate.resolveModuleArtifacts(component, context, result);
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        delegate.resolveArtifact(artifact, moduleSource, result);
    }

    public String getId() {
        return delegate.getId();
    }

    public String getName() {
        return delegate.getName();
    }
}
