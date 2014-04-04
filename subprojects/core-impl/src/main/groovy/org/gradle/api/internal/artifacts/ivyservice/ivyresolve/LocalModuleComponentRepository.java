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
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;

public class LocalModuleComponentRepository extends BaseModuleComponentRepository {
    private final ModuleMetadataProcessor processor;
    private final LocalAccess localAccess = new LocalAccess();
    private final RemoteAccess remoteAccess = new RemoteAccess();

    public LocalModuleComponentRepository(ModuleComponentRepository delegate, ModuleMetadataProcessor processor) {
        super(delegate);
        this.processor = processor;
    }

    public ModuleComponentRepositoryAccess getLocalAccess() {
        return localAccess;
    }

    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return remoteAccess;
    }

    private class LocalAccess implements ModuleComponentRepositoryAccess {
        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
            delegate.getLocalAccess().listModuleVersions(dependency, result);
            if (!result.hasResult()) {
                delegate.getRemoteAccess().listModuleVersions(dependency, result);
            }
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
            delegate.getLocalAccess().resolveComponentMetaData(dependency, moduleComponentIdentifier, result);
            if (!result.hasResult()) {
                delegate.getRemoteAccess().resolveComponentMetaData(dependency, moduleComponentIdentifier, result);
            }

            if (result.getState() == BuildableModuleVersionMetaDataResolveResult.State.Resolved) {
                processor.process(result.getMetaData());
            }
        }

        public void resolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
            delegate.getLocalAccess().resolveModuleArtifacts(component, context, result);
            if(!result.hasResult()) {
                delegate.getRemoteAccess().resolveModuleArtifacts(component, context, result);
            }
        }
    }

    private static class RemoteAccess implements ModuleComponentRepositoryAccess {
        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
            // TODO:DAZ I think this is not required
            result.missing();
        }

        public void resolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        }
    }
}
