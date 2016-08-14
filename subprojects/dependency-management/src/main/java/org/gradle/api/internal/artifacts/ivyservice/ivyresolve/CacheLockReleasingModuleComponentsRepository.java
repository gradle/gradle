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
import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

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

        @Override
        public String toString() {
            return "unlocking > " + delegate.toString();
        }

        private LockReleasingRepositoryAccess(String name, ModuleComponentRepositoryAccess delegate, CacheLockingManager cacheLockingManager) {
            this.name = name;
            this.delegate = delegate;
            this.cacheLockingManager = cacheLockingManager;
        }

        @Override
        public void listModuleVersions(final DependencyMetadata dependency, final BuildableModuleVersionListingResolveResult result) {
            cacheLockingManager.longRunningOperation("List " + dependency + " using repository " + name, new Runnable() {
                public void run() {
                    delegate.listModuleVersions(dependency, result);
                }
            });
        }

        @Override
        public void resolveComponentMetaData(final ModuleComponentIdentifier moduleComponentIdentifier,
                                             final ComponentOverrideMetadata requestMetaData, final BuildableModuleComponentMetaDataResolveResult result) {
            cacheLockingManager.longRunningOperation("Resolve " + moduleComponentIdentifier + " using repository " + name, new Runnable() {
                public void run() {
                    delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
                }
            });
        }

        @Override
        public void resolveArtifactsWithType(final ComponentResolveMetadata component, final ArtifactType artifactType, final BuildableArtifactSetResolveResult result) {
            cacheLockingManager.longRunningOperation("Resolve " + artifactType + " for " + component + " using repository " + name, new Runnable() {
                public void run() {
                    delegate.resolveArtifactsWithType(component, artifactType, result);
                }
            });
        }

        @Override
        public void resolveArtifacts(final ComponentResolveMetadata component, final BuildableComponentArtifactsResolveResult result) {
            cacheLockingManager.longRunningOperation("Resolve artifacts for " + component + " using repository " + name, new Runnable() {
                public void run() {
                    delegate.resolveArtifacts(component, result);
                }
            });
        }

        @Override
        public void resolveArtifact(final ComponentArtifactMetadata artifact, final ModuleSource moduleSource, final BuildableArtifactResolveResult result) {
            cacheLockingManager.longRunningOperation("Download " + artifact + " using repository " + name, new Runnable() {
                public void run() {
                    delegate.resolveArtifact(artifact, moduleSource, result);
                }
            });
        }
    }
}
