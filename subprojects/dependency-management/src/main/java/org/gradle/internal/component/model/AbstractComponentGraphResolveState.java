/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.component.model;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

import javax.annotation.Nullable;
import java.util.Collection;

public abstract class AbstractComponentGraphResolveState<T extends ComponentGraphResolveMetadata, S extends ComponentResolveMetadata> implements ComponentGraphResolveState, ComponentArtifactResolveState {
    private final T graphMetadata;
    private final S artifactMetadata;

    public AbstractComponentGraphResolveState(T graphMetadata, S artifactMetadata) {
        this.graphMetadata = graphMetadata;
        this.artifactMetadata = artifactMetadata;
    }

    @Override
    public ComponentIdentifier getId() {
        return graphMetadata.getId();
    }

    @Nullable
    @Override
    public ModuleSources getSources() {
        return artifactMetadata.getSources();
    }

    @Override
    public T getMetadata() {
        return graphMetadata;
    }

    public S getArtifactMetadata() {
        return artifactMetadata;
    }

    @Nullable
    @Override
    public ComponentGraphResolveState maybeAsLenientPlatform(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier) {
        return null;
    }

    @Override
    public ComponentArtifactResolveState prepareForArtifactResolution() {
        return this;
    }

    public void resolveArtifactsWithType(ArtifactResolver artifactResolver, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        artifactResolver.resolveArtifactsWithType(artifactMetadata, artifactType, result);
    }

    @Override
    public ArtifactSet prepareForArtifactResolution(ArtifactSelector artifactSelector, Collection<? extends ComponentArtifactMetadata> artifacts, ImmutableAttributes overriddenAttributes) {
        return artifactSelector.resolveArtifacts(artifactMetadata, artifacts, overriddenAttributes);
    }
}
