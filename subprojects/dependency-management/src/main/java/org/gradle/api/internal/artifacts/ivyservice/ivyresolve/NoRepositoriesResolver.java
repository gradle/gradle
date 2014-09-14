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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.model.*;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

/**
 * Used as a fallback when no repositories are defined for a given resolution.
 */
public class NoRepositoriesResolver implements RepositoryChain, DependencyToComponentIdResolver, ComponentMetaDataResolver, ArtifactResolver {
    public DependencyToComponentIdResolver getComponentIdResolver() {
        return this;
    }

    public ComponentMetaDataResolver getComponentMetaDataResolver() {
        return this;
    }

    public DependencyToComponentResolver getDependencyResolver() {
        throw new UnsupportedOperationException();
    }

    public ArtifactResolver getArtifactResolver() {
        return this;
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        result.failed(new ModuleVersionNotFoundException(dependency.getRequested(), "Cannot resolve external dependency %s because no repositories are defined."));
    }

    public void resolve(DependencyMetaData dependency, ComponentIdentifier identifier, BuildableComponentResolveResult result) {
        throw new UnsupportedOperationException();
    }

    public void resolveModuleArtifacts(ComponentResolveMetaData component, ComponentUsage usage, BuildableArtifactSetResolveResult result) {
        throw new UnsupportedOperationException();
    }

    public void resolveModuleArtifacts(ComponentResolveMetaData component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        throw new UnsupportedOperationException();
    }

    public void resolveArtifact(ComponentArtifactMetaData artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        throw new UnsupportedOperationException();
    }
}
