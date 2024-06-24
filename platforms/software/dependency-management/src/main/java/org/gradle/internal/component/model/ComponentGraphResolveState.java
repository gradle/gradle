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
import org.gradle.api.artifacts.result.ResolvedVariantResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * State for a component instance (e.g. version of a component) that is used to perform dependency graph resolution and that is independent of the particular
 * graph being resolved.
 *
 * <p>Resolution happens in multiple steps. The first step is to calculate the dependency graph, which involves selecting component instances and one or more variants of each instance.
 * This type exposes only the information and operations required to do this. In particular, it does not expose any information about artifacts unless this is actually required for graph resolution,
 * which only happens in certain specific cases (and something we should deprecate).</p>
 *
 * <p>The subsequent resolution steps to select artifacts, are performed using the instance returned by {@link #prepareForArtifactResolution()}.</p>
 *
 * <p>Instances of this type are obtained via the methods of {@link org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver} or {@link org.gradle.internal.resolve.resolver.ComponentMetaDataResolver}.</p>
 *
 * <p>This interface says nothing about thread safety, however some subtypes may be required to be thread safe.</p>
 *
 * @see ComponentGraphSpecificResolveState for dependency graph specific state for the component.
 */
public interface ComponentGraphResolveState {
    /**
     * A unique id for this component within the current build tree. Note that this id is not stable across Gradle invocations.
     */
    long getInstanceId();

    /**
     * The component identifier for this component. This identifier is stable but may not be unique.
     */
    ComponentIdentifier getId();

    /**
     * The immutable metadata for this component.
     */
    ComponentGraphResolveMetadata getMetadata();

    /**
     * Returns the public view of all variants of this component that are available for variant selection, either during graph resolution or artifact resolution.
     */
    List<ResolvedVariantResult> getAllSelectableVariantResults();

    /**
     * Returns the candidates for variant selection during graph resolution.
     */
    GraphSelectionCandidates getCandidatesForGraphVariantSelection();

    /**
     * Does this instance represent some temporary or mutated view of the component?
     *
     * Generally you should avoid retaining ad hoc instances in memory, whereas non-ad hoc instances can safely be cached for the lifetime of the build tree.
     *
     * @return true when this instance is ad hoc, false when this instance is not ad hoc and can be cached.
     */
    boolean isAdHoc();

    /**
     * When this component is a lenient platform, create a copy with the given ids. Otherwise, returns {@code null}.
     */
    @Nullable
    ComponentGraphResolveState maybeAsLenientPlatform(ModuleComponentIdentifier componentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier);

    /**
     * Creates the state that can be used for artifact resolution for this component instance.
     *
     * <p>Note that this may be expensive, and should be used only when required.</p>
     */
    ComponentArtifactResolveState prepareForArtifactResolution();
}
