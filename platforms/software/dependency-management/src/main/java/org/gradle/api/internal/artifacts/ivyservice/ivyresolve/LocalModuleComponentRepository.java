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

import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.repositories.resolver.MetadataFetchingCost;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ModuleSources;
import org.gradle.internal.resolve.result.BuildableArtifactFileResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

/**
 * A ModuleComponentRepository that wraps another, but assumes that _all_ access is local. This means that the accessor returned
 * by {@link #getLocalAccess()} will attempt both local _and_ remote access operations on the delegate.
 *
 * This is used to wrap a file-backed ExternalResourceRepository instance, so that both 'local' and 'remote' operations will
 * be considered local.
 */
public class LocalModuleComponentRepository<T> extends BaseModuleComponentRepository<T> {
    private final LocalAccess localAccess = new LocalAccess();
    private final RemoteAccess<T> remoteAccess = new RemoteAccess<>();

    public LocalModuleComponentRepository(ModuleComponentRepository<T> delegate) {
        super(delegate);
    }

    @Override
    public ModuleComponentRepositoryAccess<T> getLocalAccess() {
        return localAccess;
    }

    @Override
    public ModuleComponentRepositoryAccess<T> getRemoteAccess() {
        return remoteAccess;
    }

    private class LocalAccess implements ModuleComponentRepositoryAccess<T> {
        @Override
        public String toString() {
            return "local adapter > " + delegate;
        }

        @Override
        public void listModuleVersions(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata, BuildableModuleVersionListingResolveResult result) {
            delegate.getLocalAccess().listModuleVersions(selector, overrideMetadata, result);
            if (!result.hasResult()) {
                delegate.getRemoteAccess().listModuleVersions(selector, overrideMetadata, result);
            }
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<T> result) {
            delegate.getLocalAccess().resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
            if (!result.hasResult()) {
                delegate.getRemoteAccess().resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
            }
        }

        @Override
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            delegate.getLocalAccess().resolveArtifactsWithType(component, artifactType, result);
            if(!result.hasResult()) {
                delegate.getRemoteAccess().resolveArtifactsWithType(component, artifactType, result);
            }
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
            delegate.getLocalAccess().resolveArtifact(artifact, moduleSources, result);
            if(!result.hasResult()) {
                delegate.getRemoteAccess().resolveArtifact(artifact, moduleSources, result);
            }
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            return delegate.getRemoteAccess().estimateMetadataFetchingCost(moduleComponentIdentifier);
        }
    }

    private static class RemoteAccess<T> implements ModuleComponentRepositoryAccess<T> {
        @Override
        public String toString() {
            return "empty";
        }

        @Override
        public void listModuleVersions(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata, BuildableModuleVersionListingResolveResult result) {
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult<T> result) {
        }

        @Override
        public void resolveArtifactsWithType(ComponentArtifactResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSources moduleSources, BuildableArtifactFileResolveResult result) {
        }

        @Override
        public MetadataFetchingCost estimateMetadataFetchingCost(ModuleComponentIdentifier moduleComponentIdentifier) {
            return MetadataFetchingCost.EXPENSIVE;
        }
    }
}
