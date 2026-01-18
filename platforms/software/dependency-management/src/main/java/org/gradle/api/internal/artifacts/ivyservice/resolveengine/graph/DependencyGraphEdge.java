/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.model.DependencyMetadata;

import java.util.function.Consumer;

/**
 * A {@link ResolvedGraphDependency} that is used during the resolution of the dependency graph.
 * Additional fields in this interface are not required to reconstitute the serialized graph, but are using during construction of the graph.
 */
public interface DependencyGraphEdge extends ResolvedGraphDependency {
    DependencyGraphNode getFrom();

    boolean isTransitive();

    boolean isFromLock();

    ExcludeSpec getExclusions();

    boolean contributesArtifacts();

    DependencyMetadata getDependencyMetadata();

    /**
     * Get the attributes that are specific to this edge -- the attributes from any constraint
     * on the module that this edge points to, and any attributes attached directly to this edge.
     */
    ImmutableAttributes getAttributes();

    boolean isTargetVirtualPlatform();

    /**
     * The reason this edge contributes to component selection.
     * Overridden to enforce non-nullability. All edges have reasons, however
     * the supertype only enforces those reasons to be present in failure cases,
     * in order to avoid the overhead of serializing reasons for successful edges.
     * <p>
     * Prefer {@link #visitSelectionReasons(Consumer)}, which avoids allocations.
     */
    @Override
    ComponentSelectionReasonInternal getReason();

    /**
     * Visits all reasons this edge contributes to component selection.
     */
    void visitSelectionReasons(Consumer<ComponentSelectionDescriptorInternal> visitor);

}
