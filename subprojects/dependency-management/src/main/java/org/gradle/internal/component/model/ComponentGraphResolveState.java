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

import javax.annotation.Nullable;

/**
 * State for a component instance (e.g. version of a component) that is used to perform dependency graph resolution and that is independent of the particular
 * graph being resolved.
 *
 * <p>Resolution happens in multiple steps. The first step is to calculate the dependency graph, which involves selecting component instances and one or more variants of each instance.
 * This type exposes only the information and operations required to do this. In particular, it does not expose any information about artifacts unless this is actually required for graph resolution,
 * which only happens in certain specific cases (and something we should deprecate).</p>
 *
 * <p>The subsequent resolution steps, to select artifacts, are performed using the instance returned by {@link #prepareForArtifactResolution()}.</p>
 *
 * <p>Instances of this type are obtained via the methods of {@link org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver} or {@link org.gradle.internal.resolve.resolver.ComponentMetaDataResolver}.</p>
 *
 * <p>This interface says nothing about thread safety, however some subtypes may be required to be thread safe.</p>
 *
 * @see ComponentGraphSpecificResolveState for dependency graph specific state for the component.
 */
public interface ComponentGraphResolveState {
    ComponentIdentifier getId();

    @Nullable
    ModuleSources getSources();

    ComponentGraphResolveMetadata getMetadata();

    /**
     * When this component is a lenient platform, create a copy with the given ids. Otherwise returns {@code null}.
     */
    @Nullable
    ComponentGraphResolveState maybeAsLenientPlatform(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier);

    /**
     * Determines the set of artifacts for the given variant of this component.
     *
     * <p>Note that this may be expensive, for example it may block waiting for access to the source project or for network or IO requests to the source repository, and should be used only when
     * required.
     */
    VariantArtifactGraphResolveMetadata resolveArtifactsFor(VariantGraphResolveMetadata variant);

    /**
     * Creates the state that can be used for artifact resolution for this component instance.
     *
     * <p>Note that this may be expensive, and should be used only when required.</p>
     */
    ComponentArtifactResolveState prepareForArtifactResolution();
}
