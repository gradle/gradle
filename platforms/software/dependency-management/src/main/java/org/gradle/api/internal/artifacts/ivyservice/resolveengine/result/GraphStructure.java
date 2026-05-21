/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.jspecify.annotations.Nullable;

/**
 * An individually addressable view of a resolved dependency graph, where each
 * element of the graph may be accessed without walking an object graph.
 */
public interface GraphStructure {

    /**
     * Get all nodes in the graph.
     */
    Nodes nodes();

    /**
     * Get all edges in the graph. Edges are directed
     * from some source node to some target node.
     */
    Edges edges();

    /**
     * Get all components in the graph. Components own the nodes
     * in the graph but do not have and are not the target of edges.
     */
    Components components();

    /**
     * A view of the nodes in the graph.
     */
    interface Nodes {

        /**
         * The index of the root node in the graph.
         */
        int root();

        /**
         * The number of nodes in the graph.
         */
        int count();

        /**
         * The index of the component that owns the node with the given index.
         */
        int owner(int index);

        /**
         * The attributes of the node with the given index.
         */
        ImmutableAttributes attributes(int index);

        /**
         * The capabilities of the node with the given index.
         */
        ImmutableCapabilities capabilities(int index);

        /**
         * The name of the variant backing the node with the given index.
         */
        String variantName(int index);

        /**
         * The index of the external variant of the node with the given index,
         * or -1 if the node does not have an external variant.
         */
        int externalVariantIndex(int index);

    }

    /**
     * A view of the edges between the nodes of the graph.
     */
    interface Edges {

        /**
         * The start index of edges for the node with the given index.
         */
        int start(int nodeIndex);

        /**
         * The end index of edges for the node with the given index.
         */
        int end(int nodeIndex);

        /**
         * The selector of the edge with the given index.
         */
        ComponentSelector selector(int index);

        /**
         * True if the edge with the given index is a constraint,
         * false otherwise.
         */
        boolean constraint(int index);

        /**
         * The index of the target node of the edge with the given index,
         * or -1 if the edge has a failure.
         */
        int targetNode(int index);

        /**
         * The failure of the edge with the given index. May be called if
         * {@link #targetNode(int)} returns -1.
         *
         * @throws IllegalArgumentException if the edge has no failure.
         */
        EdgeFailure failure(int index);

        /**
         * Details describing an edge that failed to attach to a target node.
         *
         * @param failure The exception that caused the failure.
         * @param reason The reason the edge failed to attach.
         */
        record EdgeFailure(
            ModuleVersionResolveException failure,
            ComponentSelectionReasonInternal reason
        ) { }

    }

    /**
     * A view of the components that own the nodes in the graph.
     */
    interface Components {

        /**
         * The number of components in the graph.
         */
        int count();

        /**
         * The identifier of the component with the given index.
         */
        ComponentIdentifier id(int index);

        /**
         * The name of the repository that provided the component with the given index.
         */
        @Nullable String repositoryName(int index);

        /**
         * The reason the component with the given index was selected.
         */
        ComponentSelectionReasonInternal selectionReason(int index);

        /**
         * The module version identifier of the component with the given index.
         */
        ModuleVersionIdentifier moduleVersionId(int index);

    }

}
