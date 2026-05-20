/*
 * Copyright 2012 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntList;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class DefaultResolvedComponentResult implements ResolvedComponentResultInternal {

    private final int index;
    private final IntList nodeIndices;
    private final ResolvedGraphResult graph;

    private @Nullable ImmutableList<ResolvedVariantResult> variants;
    private @Nullable Set<? extends DependencyResult> cachedComponentDependencies;
    private @Nullable List<@Nullable ImmutableList<DependencyResult>> variantDependencies;

    public DefaultResolvedComponentResult(
        int index,
        IntList nodeIndices,
        ResolvedGraphResult graph
    ) {
        this.index = index;
        this.nodeIndices = nodeIndices;
        this.graph = graph;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public ResolvedGraphResult graph() {
        return graph;
    }

    @Override
    public ComponentIdentifier getId() {
        return graph.structure().components().id(index);
    }

    @Override
    @Deprecated
    public @Nullable String getRepositoryName() {
        return graph.structure().components().repositoryName(index);
    }

    @Override
    public @Nullable String getRepositoryId() {
        return graph.structure().components().repositoryName(index);
    }

    @Override
    public Set<? extends DependencyResult> getDependencies() {
        if (cachedComponentDependencies == null) {
            ImmutableSet.Builder<DependencyResult> builder = ImmutableSet.builder();
            for (ResolvedVariantResult variant : getVariants()) {
                for (DependencyResult dependency : getDependenciesForVariant(variant)) {
                    builder.add(dependency);
                }
            }
            this.cachedComponentDependencies = builder.build();
        }
        return cachedComponentDependencies;
    }

    @Override
    public Set<? extends ResolvedDependencyResult> getDependents() {
        return graph.getIncomingEdges(index);
    }

    @Override
    public ComponentSelectionReasonInternal getSelectionReason() {
        return graph.structure().components().selectionReason(index);
    }

    @Override
    public @Nullable ModuleVersionIdentifier getModuleVersion() {
        return graph.structure().components().moduleVersionId(index);
    }

    @Override
    public String toString() {
        return getId().getDisplayName();
    }

    @Override
    public List<ResolvedVariantResult> getVariants() {
        if (variants == null) {
            int size = nodeIndices.size();
            ImmutableList.Builder<ResolvedVariantResult> builder = ImmutableList.builderWithExpectedSize(size);
            for (int i = 0; i < size; i++) {
                builder.add(graph.getVariant(nodeIndices.getInt(i)));
            }
            this.variants = builder.build();
        }
        return variants;
    }

    @Override
    public List<ResolvedVariantResult> getAvailableVariants() {
        List<ResolvedVariantResult> availableVariants = graph.getAvailableVariants(index);
        if (availableVariants == null) {
            return getVariants();
        }
        return availableVariants;
    }

    @Override
    public List<DependencyResult> getDependenciesForVariant(ResolvedVariantResult variant) {
        List<ResolvedVariantResult> selectedVariants = getVariants();
        int indexInComponent = selectedVariants.indexOf(variant);
        if (indexInComponent == -1) {
            Optional<ResolvedVariantResult> sameName = selectedVariants.stream()
                .filter(v -> v.getDisplayName().equals(variant.getDisplayName()))
                .findFirst();
            String moreInfo = sameName.isPresent()
                ? "A variant with the same name exists but is not the same instance."
                : "There's no resolved variant with the same name.";
            throw new InvalidUserCodeException("Variant '" + variant.getDisplayName() + "' doesn't belong to resolved component '" + this + "'. " + moreInfo + " Most likely you are using a variant from another component to get the dependencies of this component.");
        }

        if (variantDependencies == null) {
            variantDependencies = new ArrayList<>(selectedVariants.size());
            for (int i = 0; i < selectedVariants.size(); i++) {
                variantDependencies.add(null);
            }
        }

        ImmutableList<DependencyResult> result = variantDependencies.get(indexInComponent);
        if (result == null) {
            GraphStructure.Edges edges = graph.structure().edges();

            int nodeIndex = nodeIndices.getInt(indexInComponent);
            int start = edges.start(nodeIndex);
            int end = edges.end(nodeIndex);
            ImmutableSet.Builder<DependencyResult> builder = ImmutableSet.builderWithExpectedSize(end - start);
            for (int i = start; i < end; i++) {
                ComponentSelector selector = edges.selector(i);
                boolean constraint = edges.constraint(i);
                int targetNodeIndex = edges.targetNode(i);
                if (targetNodeIndex != -1) {
                    int targetComponentIndex = graph.structure().nodes().owner(targetNodeIndex);
                    builder.add(new DefaultResolvedDependencyResult(
                        selector,
                        constraint,
                        this,
                        graph.getComponent(targetComponentIndex),
                        graph.getVariant(targetNodeIndex)
                    ));
                } else {
                    GraphStructure.Edges.EdgeFailure failure = edges.failure(i);
                    builder.add(new DefaultUnresolvedDependencyResult(
                        selector,
                        this,
                        constraint,
                        failure.failure(),
                        failure.reason()
                    ));
                }
            }
            result = builder.build().asList();
            variantDependencies.set(indexInComponent, result);
        }

        return result;
    }

    /**
     * A recursive function that traverses the dependency graph of a given module and acts on each node and edge encountered.
     *
     * @param start A ResolvedComponentResult node, which represents the entry point into the sub-section of the dependency
     * graph to be traversed
     * @param moduleAction an action to be performed on each node (module) in the graph
     * @param dependencyAction an action to be performed on each edge (dependency) in the graph
     * @param visited tracks the visited nodes during the recursive traversal
     */
    // TODO: Internal consumers of this method should prefer to operate directly on a GraphStructure,
    // which does not incur the performance penalities of building the ResolutionResult public API.
    public static void eachElement(
        ResolvedComponentResult start,
        Action<? super ResolvedComponentResult> moduleAction,
        Action<? super DependencyResult> dependencyAction,
        Set<ResolvedComponentResult> visited
    ) {
        if (!visited.add(start)) {
            return;
        }
        moduleAction.execute(start);
        for (DependencyResult d : start.getDependencies()) {
            dependencyAction.execute(d);
            if (d instanceof ResolvedDependencyResult) {
                eachElement(((ResolvedDependencyResult) d).getSelected(), moduleAction, dependencyAction, visited);
            }
        }
    }

}
