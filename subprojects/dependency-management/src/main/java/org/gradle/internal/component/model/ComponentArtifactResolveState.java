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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ArtifactSelector;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

import javax.annotation.Nullable;
import java.util.Collection;

/**
 * State for a component instance that is used to perform artifact resolution.
 */
public interface ComponentArtifactResolveState {
    ComponentIdentifier getId();

    @Nullable
    ModuleSources getSources();

    VariantArtifactResolveState prepareForArtifactResolution(VariantGraphResolveMetadata variant);

    /**
     * Discovers the set of artifacts belonging to the given component, with the type specified. Does not download the artifacts. Any failures are packaged up in the result.
     */
    void resolveArtifactsWithType(ArtifactResolver artifactResolver, ArtifactType artifactType, BuildableArtifactSetResolveResult result);

    /**
     * Creates a set that will resolve the given artifacts of the given component.
     */
    ArtifactSet resolveArtifacts(ArtifactSelector artifactSelector, Collection<? extends ComponentArtifactMetadata> artifacts, ImmutableAttributes overriddenAttributes);
}
