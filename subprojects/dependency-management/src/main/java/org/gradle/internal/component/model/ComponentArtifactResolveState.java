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
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;

import java.util.List;
import java.util.Optional;

/**
 * State for a component instance that is used to perform artifact resolution.
 *
 * <p>Resolution happens in multiple steps. The first is to calculate the dependency graph, and the subsequent steps select artifacts. Artifact resolution is broken down into 3 main steps:</p>
 * <ul>
 *     <li>Select a variant of the component instance. The variant selected for artifact resolution may be different to that used for graph resolution,
 *     for example when using an {@link org.gradle.api.artifacts.ArtifactView} to select different variants.</li>
 *     <li>Determine how to produce the artifacts of the variant, for example by running a chain of transformers.</li>
 *     <li>Produce the artifacts, for example by running the transforms or downloading files.</li>
 * </ul>
 *
 * <p>This interface says nothing about thread safety, however some subtypes may be required to be thread safe.</p>
 *
 * <p>Instances of this type are created using {@link ComponentGraphResolveState#prepareForArtifactResolution()}.</p>
 */
public interface ComponentArtifactResolveState {
    ComponentIdentifier getId();

    ComponentArtifactResolveMetadata getResolveMetadata();

    /**
     * Discovers the set of artifacts belonging to this component, with the type specified. Does not download the artifacts. Any failures are packaged up in the result.
     */
    void resolveArtifactsWithType(ArtifactResolver artifactResolver, ArtifactType artifactType, BuildableArtifactSetResolveResult result);

    /**
     * Return the artifact resolution state for each variant in this component, used for selecting artifacts.
     */
    Optional<List<VariantArtifactResolveState>> getVariantsForArtifactSelection();
}
