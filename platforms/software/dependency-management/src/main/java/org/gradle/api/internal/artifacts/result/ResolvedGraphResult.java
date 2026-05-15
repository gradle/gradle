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

package org.gradle.api.internal.artifacts.result;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure;
import org.gradle.internal.Describables;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared state that all components and variants of a
 * {@link org.gradle.api.artifacts.result.ResolutionResult} share.
 */
public class ResolvedGraphResult {

    private final GraphStructure structure;
    private final @Nullable List<List<ResolvedVariantResult>> availableVariantsByComponent;

    private final List<IntList> nodesByComponent;

    private @Nullable ResolvedComponentResultInternal @Nullable [] components;
    private @Nullable ResolvedVariantResult @Nullable [] variants;
    private @Nullable List<Set<ResolvedDependencyResult>> resolvedEdgesByTarget;

    public ResolvedGraphResult(
        GraphStructure structure,
        @Nullable List<List<ResolvedVariantResult>> availableVariantsByComponent
    ) {
        this.structure = structure;
        this.availableVariantsByComponent = availableVariantsByComponent;

        this.nodesByComponent = computeNodeIndices(structure);
    }

    /**
     * Get the underlying raw graph structure that this resolved graph is based on.
     */
    public GraphStructure structure() {
        return structure;
    }

    /**
     * Get the component at the given index.
     */
    public ResolvedComponentResultInternal getComponent(int index) {
        if (components == null) {
            components = new ResolvedComponentResultInternal[structure.components().count()];
        }
        ResolvedComponentResultInternal component = components[index];
        if (component == null) {
            component = new DefaultResolvedComponentResult(
                index,
                nodesByComponent.get(index),
                this
            );
            components[index] = component;
        }
        return component;
    }

    /**
     * Get the variant at the given index.
     */
    public ResolvedVariantResult getVariant(int index) {
        if (variants == null) {
            variants = new ResolvedVariantResult[structure.nodes().count()];
        }
        ResolvedVariantResult variant = variants[index];
        if (variant == null) {
            GraphStructure.Nodes nodes = structure.nodes();
            int externalVariantIndex = nodes.externalVariantIndex(index);

            ResolvedVariantResult externalVariant = null;
            if (externalVariantIndex != -1) {
                externalVariant = getVariant(externalVariantIndex);
            }

            variant = new DefaultResolvedVariantResult(
                structure.components().id(nodes.owner(index)),
                Describables.of(nodes.variantName(index)),
                nodes.attributes(index),
                nodes.capabilities(index).asSet().asList(),
                externalVariant
            );
            variants[index] = variant;
        }
        return variant;
    }

    private static List<IntList> computeNodeIndices(GraphStructure structure) {
        int componentCount = structure.components().count();
        List<IntList> nodesByComponent = new ArrayList<>(componentCount);
        for (int i = 0; i < componentCount; i++) {
            nodesByComponent.add(new IntArrayList());
        }

        GraphStructure.Nodes nodes = structure.nodes();
        for (int i = 0; i < nodes.count(); i++) {
            int ownerId = nodes.owner(i);
            nodesByComponent.get(ownerId).add(i);
        }
        return nodesByComponent;
    }

    /**
     * Get all incoming edges for the component at the given index.
     */
    public Set<ResolvedDependencyResult> getIncomingEdges(int targetComponentIndex) {
        if (resolvedEdgesByTarget == null) {
            int count = structure.components().count();
            this.resolvedEdgesByTarget = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                resolvedEdgesByTarget.add(new LinkedHashSet<>());
            }
            for (int i = 0; i < count; i++) {
                ResolvedComponentResultInternal component = getComponent(i);
                for (DependencyResult dependency : component.getDependencies()) {
                    if (dependency instanceof ResolvedDependencyResult resolved) {
                        ResolvedComponentResultInternal targetComponent = (ResolvedComponentResultInternal) resolved.getSelected();
                        resolvedEdgesByTarget.get(targetComponent.index()).add(resolved);
                    }
                }
            }
        }
        return resolvedEdgesByTarget.get(targetComponentIndex);
    }

    /**
     * Get the available variants for the component at the given index, if any.
     */
    public @Nullable List<ResolvedVariantResult> getAvailableVariants(int componentIndex) {
        if (availableVariantsByComponent == null) {
            return null;
        }
        return availableVariantsByComponent.get(componentIndex);
    }

    public @Nullable List<List<ResolvedVariantResult>> availableVariantsByComponent() {
        return availableVariantsByComponent;
    }

}
