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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.specs.Spec;

/**
 * A set of parameters governing the selection of artifacts from a dependency graph.
 */
public class ArtifactSelectionSpec {

    private final ImmutableAttributes requestAttributes;
    private final Spec<? super ComponentIdentifier> componentFilter;
    private final boolean selectFromAllVariants;
    private final boolean allowNoMatchingVariants;

    public ArtifactSelectionSpec(
        ImmutableAttributes requestAttributes,
        Spec<? super ComponentIdentifier> componentFilter,
        boolean selectFromAllVariants,
        boolean allowNoMatchingVariants
    ) {
        this.requestAttributes = requestAttributes;
        this.componentFilter = componentFilter;
        this.selectFromAllVariants = selectFromAllVariants;
        this.allowNoMatchingVariants = allowNoMatchingVariants;
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
     * If false, selection is restricted only to the artifacts exposed a selected node in the graph.
     * If true, selection is expanded to include artifacts from any variant exposed by the component that a given node belongs to.
     */
    public boolean getSelectFromAllVariants() {
        return selectFromAllVariants;
    }

    /**
     * If false, selection will fail if no matching artifact variants are found for a given graph node.
     */
    public boolean getAllowNoMatchingVariants() {
        return allowNoMatchingVariants;
    }

}
