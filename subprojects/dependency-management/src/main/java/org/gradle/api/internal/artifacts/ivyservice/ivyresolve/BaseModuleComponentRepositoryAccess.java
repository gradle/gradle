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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

public class BaseModuleComponentRepositoryAccess<T> implements ModuleComponentRepositoryAccess<T> {
    private final ModuleComponentRepositoryAccess<T> delegate;

    public BaseModuleComponentRepositoryAccess(ModuleComponentRepositoryAccess<T> delegate) {
        this.delegate = delegate;
    }

    public ModuleComponentRepositoryAccess<T> getDelegate() {
        return delegate;
    }

    @Override
    public void listModuleVersions(ModuleDependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
        delegate.listModuleVersions(dependency, result);
    }

    @Override
    public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<T> result) {
        delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        delegate.resolveArtifactsWithType(component, artifactType, result);
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
        delegate.resolveArtifact(artifact, moduleSources, result);
    }

    @Override
    public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
        return delegate.estimateMetadataFetchingCost(moduleComponentIdentifier);
    }
}
