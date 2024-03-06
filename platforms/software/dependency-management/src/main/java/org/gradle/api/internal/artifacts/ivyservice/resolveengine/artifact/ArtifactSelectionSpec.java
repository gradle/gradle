/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.specs.Spec;

import javax.annotation.Nullable;

/**
 * A set of parameters governing the selection of artifacts from a dependency graph.
 */
public class ArtifactSelectionSpec {

    private final ImmutableAttributes requestAttributes;
    private final Spec<? super ComponentIdentifier> componentFilter;
    private final VariantReselectionSpec variantReselectionSpec;
    private final boolean allowNoMatchingVariants;
    private final ResolutionStrategy.SortOrder sortOrder;

    public ArtifactSelectionSpec(
        ImmutableAttributes requestAttributes,
        Spec<? super ComponentIdentifier> componentFilter,
        @Nullable VariantReselectionSpec variantReselectionSpec,
        boolean allowNoMatchingVariants,
        ResolutionStrategy.SortOrder sortOrder
    ) {
        this.requestAttributes = requestAttributes;
        this.componentFilter = componentFilter;
        this.variantReselectionSpec = variantReselectionSpec;
        this.allowNoMatchingVariants = allowNoMatchingVariants;
        this.sortOrder = sortOrder;
    }

    /**
     * The request attributes used to determine which variant of each graph node to select.
     */
    public ImmutableAttributes getRequestAttributes() {
        return requestAttributes;
    }

    /**
     * Filters the selected artifacts to only contain those which originated from a component matching this filter.
     */
    public Spec<? super ComponentIdentifier> getComponentFilter() {
        return componentFilter;
    }

    /**
     * If null, selection is restricted only to the artifacts exposed a selected node in the graph.
     * If present, selection is expanded to include artifacts from any variant exposed by the component that a given node belongs to.
     */
    @Nullable
    public VariantReselectionSpec getVariantReselectionSpec() {
        return variantReselectionSpec;
    }

    /**
     * If false, selection will fail if no matching artifact variants are found for a given graph node.
     */
    public boolean getAllowNoMatchingVariants() {
        return allowNoMatchingVariants;
    }

    /**
     * The order that artifacts should be sorted after selection.
     */
    public ResolutionStrategy.SortOrder getSortOrder() {
        return sortOrder;
    }

    /**
     * Controls how variant reselection is performed.
     */
    public static class VariantReselectionSpec {

        private final boolean selectFromAllCapabilities;

        public VariantReselectionSpec(boolean selectFromAllCapabilities) {
            this.selectFromAllCapabilities = selectFromAllCapabilities;
        }

        /**
         * If true, all matching variants from all capabilities are selected.
         * If false, standard graph variant matching is performed on the target component, and
         * only a single variant is selected that matches the requested capabilities from the
         * original dependency.
         */
        public boolean getSelectFromAllCapabilities() {
            return selectFromAllCapabilities;
        }
    }
}
