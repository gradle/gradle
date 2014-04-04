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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.BuildableArtifactSetResolveResult;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;

public class LocalAwareModuleComponentRepository implements ModuleComponentRepository {
    private final ModuleComponentRepositoryAccess localAccess;
    private final ModuleComponentRepositoryAccess remoteAccess;
    private final LocalAwareModuleVersionRepository adapted;

    public LocalAwareModuleComponentRepository(LocalAwareModuleVersionRepository adapted) {
        this.adapted = adapted;
        this.localAccess = new LocalModuleComponentRepositoryAccess(adapted);
        this.remoteAccess = new RemoteModuleComponentRepositoryAccess(adapted);
    }

    public String getId() {
        return adapted.getId();
    }

    public String getName() {
        return adapted.getName();
    }

    public ModuleComponentRepositoryAccess getLocalAccess() {
        return localAccess;
    }

    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return remoteAccess;
    }

    public void resolveModuleArtifacts(ComponentMetaData component, ArtifactResolveContext context, BuildableArtifactSetResolveResult result) {
        adapted.resolveModuleArtifacts(component, context, result);
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        adapted.resolveArtifact(artifact, moduleSource, result);
    }

    private static class LocalModuleComponentRepositoryAccess implements ModuleComponentRepositoryAccess {
        private final LocalAwareModuleVersionRepository adapted;

        private LocalModuleComponentRepositoryAccess(LocalAwareModuleVersionRepository adapted) {
            this.adapted = adapted;
        }

        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
            adapted.localListModuleVersions(dependency, result);
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
            adapted.localResolveComponentMetaData(dependency, moduleComponentIdentifier, result);
        }
    }

    private static class RemoteModuleComponentRepositoryAccess implements ModuleComponentRepositoryAccess {
        private final LocalAwareModuleVersionRepository adapted;

        private RemoteModuleComponentRepositoryAccess(LocalAwareModuleVersionRepository adapted) {
            this.adapted = adapted;
        }

        public void listModuleVersions(DependencyMetaData dependency, BuildableModuleVersionSelectionResolveResult result) {
            adapted.listModuleVersions(dependency, result);
        }

        public void resolveComponentMetaData(DependencyMetaData dependency, ModuleComponentIdentifier moduleComponentIdentifier, BuildableModuleVersionMetaDataResolveResult result) {
            adapted.resolveComponentMetaData(dependency, moduleComponentIdentifier, result);
        }
    }


}
