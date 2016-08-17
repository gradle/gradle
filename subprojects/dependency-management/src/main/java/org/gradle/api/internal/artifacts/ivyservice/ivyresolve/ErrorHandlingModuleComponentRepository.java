/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;

public class ErrorHandlingModuleComponentRepository implements ModuleComponentRepository {
    private final ModuleComponentRepository delegate;
    private final ErrorHandlingModuleComponentRepositoryAccess local;
    private final ErrorHandlingModuleComponentRepositoryAccess remote;

    public ErrorHandlingModuleComponentRepository(ModuleComponentRepository delegate) {
        this.delegate = delegate;
        local = new ErrorHandlingModuleComponentRepositoryAccess(delegate.getLocalAccess());
        remote = new ErrorHandlingModuleComponentRepositoryAccess(delegate.getRemoteAccess());
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public ModuleComponentRepositoryAccess getLocalAccess() {
        return local;
    }

    @Override
    public ModuleComponentRepositoryAccess getRemoteAccess() {
        return remote;
    }

    private static final class ErrorHandlingModuleComponentRepositoryAccess implements ModuleComponentRepositoryAccess {
        private final ModuleComponentRepositoryAccess delegate;

        public ErrorHandlingModuleComponentRepositoryAccess(ModuleComponentRepositoryAccess delegate) {
            this.delegate = delegate;
        }

        @Override
        public String toString() {
            return "error handling > " + delegate.toString();
        }

        @Override
        public void listModuleVersions(DependencyMetadata dependency, BuildableModuleVersionListingResolveResult result) {
            try {
                delegate.listModuleVersions(dependency, result);
            } catch (Throwable throwable) {
                result.failed(new ModuleVersionResolveException(dependency.getSelector(), throwable));
            }
        }

        @Override
        public void resolveComponentMetaData(ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata requestMetaData, BuildableModuleComponentMetaDataResolveResult result) {
            try {
                delegate.resolveComponentMetaData(moduleComponentIdentifier, requestMetaData, result);
            } catch (Throwable throwable) {
                result.failed(new ModuleVersionResolveException(moduleComponentIdentifier, throwable));
            }
        }

        @Override
        public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
            try {
                delegate.resolveArtifactsWithType(component, artifactType, result);
            } catch (Throwable throwable) {
                result.failed(new ArtifactResolveException(component.getComponentId(), throwable));
            }
        }

        @Override
        public void resolveArtifacts(ComponentResolveMetadata component, BuildableComponentArtifactsResolveResult result) {
            try {
                delegate.resolveArtifacts(component, result);
            } catch (Throwable throwable) {
                result.failed(new ArtifactResolveException(component.getComponentId(), throwable));
            }
        }

        @Override
        public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
            try {
                delegate.resolveArtifact(artifact, moduleSource, result);
            } catch (Throwable throwable) {
                result.failed(new ArtifactResolveException(artifact.getId(), throwable));
            }
        }
    }
}
