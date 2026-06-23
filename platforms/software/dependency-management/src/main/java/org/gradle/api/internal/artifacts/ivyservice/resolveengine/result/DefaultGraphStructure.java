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

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntList;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.jspecify.annotations.Nullable;

import java.util.BitSet;
import java.util.List;

/**
 * Default implementation of {@link GraphStructure}.
 */
public record DefaultGraphStructure(
    DefaultNodes nodes,
    DefaultEdges edges,
    DefaultComponents components
) implements GraphStructure {

    public record DefaultNodes(
        int root,
        IntList owners,
        ImmutableList<ImmutableAttributes> attributes,
        ImmutableList<ImmutableCapabilities> capabilities,
        ImmutableList<String> variantNames,
        Int2IntMap externalVariantIndices
    ) implements Nodes {

        @Override
        public int count() {
            return owners.size();
        }

        @Override
        public int owner(int index) {
            return owners.getInt(index);
        }

        @Override
        public ImmutableAttributes attributes(int index) {
            return attributes.get(index);
        }

        @Override
        public ImmutableCapabilities capabilities(int index) {
            return capabilities.get(index);
        }

        @Override
        public String variantName(int index) {
            return variantNames.get(index);
        }

        @Override
        public int externalVariantIndex(int index) {
            return externalVariantIndices.getOrDefault(index, -1);
        }

    }

    public record DefaultEdges(
        IntList indices,
        ImmutableList<ComponentSelector> selectors,
        BitSet constraints,
        IntList targetNodeIndices,
        Int2ObjectMap<EdgeFailure> failures
    ) implements Edges {

        @Override
        public int start(int nodeIndex) {
            return indices.getInt(nodeIndex);
        }

        @Override
        public int end(int nodeIndex) {
            return indices.getInt(nodeIndex + 1);
        }

        @Override
        public ComponentSelector selector(int index) {
            return selectors.get(index);
        }

        @Override
        public boolean constraint(int index) {
            return constraints.get(index);
        }

        @Override
        public int targetNode(int index) {
            return targetNodeIndices.getInt(index);
        }

        @Override
        public EdgeFailure failure(int index) {
            EdgeFailure failure = failures.get(index);
            if (failure == null) {
                throw new IllegalArgumentException("No failure for edge " + index);
            }
            return failure;
        }

    }

    public record DefaultComponents(
        ImmutableList<ComponentSelectionReasonInternal> selectionReasons,
        List<@Nullable String> repositoryNames,
        ImmutableList<ComponentIdentifier> ids,
        ImmutableList<ModuleVersionIdentifier> moduleVersionIds
    ) implements Components {

        @Override
        public int count() {
            return ids.size();
        }

        @Override
        public ComponentIdentifier id(int componentIndex) {
            return ids.get(componentIndex);
        }

        @Override
        public ComponentSelectionReasonInternal selectionReason(int componentIndex) {
            return selectionReasons.get(componentIndex);
        }

        @Override
        public @Nullable String repositoryName(int componentIndex) {
            return repositoryNames.get(componentIndex);
        }

        @Override
        public ModuleVersionIdentifier moduleVersionId(int componentIndex) {
            return moduleVersionIds.get(componentIndex);
        }

    }

}
