/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.internal.component.model.*;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentVersionSelectionResolveResult;

/**
 * A wrapper around a {@link ModuleComponentRepository} that handles releasing the cache lock before making remote calls.
 */
public class CacheLockReleasingModuleComponentsRepository extends BaseModuleComponentRepository {
    private final ModuleComponentRepositoryAccess remoteAccess;

    public CacheLockReleasingModuleComponentsRepository(ModuleComponentRepository repository, CacheLockingManager cacheLockingManager) {
        super(repository);
        this.remoteAccess = new LockReleasingRepositoryAccess(repository.getName(), repository.getRemoteAccess(), cacheLockingManager);
    }

    @Override
    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return remoteAccess;
    }

    private static class LockReleasingRepositoryAccess implements ModuleComponentRepositoryAccess {
        private final String name;
        private final ModuleComponentRepositoryAccess delegate;
        private final CacheLockingManager cacheLockingManager;

        private LockReleasingRepositoryAccess(String name, ModuleComponentRepositoryAccess delegate, CacheLockingManager cacheLockingManager) {
            this.name = name;
            this.delegate = delegate;
            this.cacheLockingManager = cacheLockingManager;
        }

        public void listModuleVersions(final DependencyMetaData dependency, final BuildableModuleComponentVersionSelectionResolveResult result) {
            cacheLockingManager.longRunningOperation(String.format("List %s using repository %s", dependency, name), new Runnable() {
                public void run() {
                    delegate.listModuleVersions(dependency, result);
                }
            });
        }

        public void resolveComponentMetaData(final DependencyMetaData dependency, final ModuleComponentIdentifier moduleComponentIdentifier, final BuildableModuleComponentMetaDataResolveResult result) {
            cacheLockingManager.longRunningOperation(String.format("Resolve %s using repository %s", dependency, name), new Runnable() {
                public void run() {
                    delegate.resolveComponentMetaData(dependency, moduleComponentIdentifier, result);
                }
            });
        }

        public void resolveModuleArtifacts(final ComponentResolveMetaData component, final ArtifactType artifactType, final BuildableArtifactSetResolveResult result) {
            cacheLockingManager.longRunningOperation(String.format("Resolve %s for %s using repository %s", artifactType, component, name), new Runnable() {
                public void run() {
                    delegate.resolveModuleArtifacts(component, artifactType, result);
                }
            });
        }

        public void resolveModuleArtifacts(final ComponentResolveMetaData component, final ComponentUsage componentUsage, final BuildableArtifactSetResolveResult result) {
            cacheLockingManager.longRunningOperation(String.format("Resolve %s for %s using repository %s", componentUsage, component, name), new Runnable() {
                public void run() {
                    delegate.resolveModuleArtifacts(component, componentUsage, result);
                }
            });
        }


        public void resolveArtifact(final ComponentArtifactMetaData artifact, final ModuleSource moduleSource, final BuildableArtifactResolveResult result) {
            cacheLockingManager.longRunningOperation(String.format("Download %s using repository %s", artifact, name), new Runnable() {
                public void run() {
                    delegate.resolveArtifact(artifact, moduleSource, result);
                }
            });
        }
    }
}
