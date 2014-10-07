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
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.internal.component.model.*;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.component.model.ComponentResolveMetaData;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentVersionSelectionResolveResult;

public class LocalModuleComponentRepository extends BaseModuleComponentRepository {
    private final ComponentMetadataProcessor metadataProcessor;
    private final LocalAccess localAccess = new LocalAccess();
    private final RemoteAccess remoteAccess = new RemoteAccess();

    public LocalModuleComponentRepository(ModuleComponentRepository delegate, ComponentMetadataProcessor metadataProcessor) {
        super(delegate);
        this.metadataProcessor = metadataProcessor;
    }

    public ModuleComponentRepositoryAccess getLocalAccess() {
        return localAccess;
    }

    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return remoteAccess;
    }

    private class LocalAccess implements ModuleComponentRepositoryAccess {
        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleComponentVersionSelectionResolveResult result) {
            delegate.getLocalAccess().listModuleVersions(dependency, result);
            if (!result.hasResult()) {
                delegate.getRemoteAccess().listModuleVersions(dependency, result);
            }
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleComponentMetaDataResolveResult result) {
            delegate.getLocalAccess().resolveComponentMetaData(dependency, moduleComponentIdentifier, result);
            if (!result.hasResult()) {
                delegate.getRemoteAccess().resolveComponentMetaData(dependency, moduleComponentIdentifier, result);
            }

            if (result.getState() == BuildableModuleComponentMetaDataResolveResult.State.Resolved) {
                metadataProcessor.processMetadata(result.getMetaData());
            }
        }

        public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            delegate.getLocalAccess().resolveModuleArtifacts(component, artifactType, result);
            if(!result.hasResult()) {
                delegate.getRemoteAccess().resolveModuleArtifacts(component, artifactType, result);
            }
        }

        public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage componentUsage, BuildableArtifactSetResolveResult result) {
            delegate.getLocalAccess().resolveModuleArtifacts(component, componentUsage, result);
            if(!result.hasResult()) {
                delegate.getRemoteAccess().resolveModuleArtifacts(component, componentUsage, result);
            }
        }

        public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
            delegate.getLocalAccess().resolveArtifact(artifact, moduleSource, result);
            if(!result.hasResult()) {
                delegate.getRemoteAccess().resolveArtifact(artifact, moduleSource, result);
            }
        }
    }

    private static class RemoteAccess implements ModuleComponentRepositoryAccess {
        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleComponentVersionSelectionResolveResult result) {
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleComponentMetaDataResolveResult result) {
        }

        public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        }

        public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage componentUsage, BuildableArtifactSetResolveResult result) {
        }

        public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        }
    }
}
