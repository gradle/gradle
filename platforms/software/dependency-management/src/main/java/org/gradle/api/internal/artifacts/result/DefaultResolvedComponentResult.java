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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DefaultResolvedComponentResult implements ResolvedComponentResultInternal {

    private final ModuleVersionIdentifier moduleVersion;
    private Set<ResolvedDependencyResult> dependents = new LinkedHashSet<>();
    private final ComponentSelectionReason selectionReason;
    private final ComponentIdentifier componentId;
    private final ImmutableList<ResolvedVariantResult> selectedVariants;
    private final Map<Long, ResolvedVariantResult> selectedVariantsById;
    private final ImmutableList<ResolvedVariantResult> allVariants;
    private final String repositoryName;
    private Map<ResolvedVariantResult, ImmutableSet<DependencyResult>> variantDependencies = new LinkedHashMap<>();

    private @Nullable Set<DependencyResult> cachedComponentDependencies;

    public DefaultResolvedComponentResult(
        ModuleVersionIdentifier moduleVersion,
        ComponentSelectionReason selectionReason,
        ComponentIdentifier componentId,
        ImmutableMap<Long, ResolvedVariantResult> selectedVariants,
        ImmutableList<ResolvedVariantResult> allVariants,
        @Nullable String repositoryName
    ) {
        this.moduleVersion = moduleVersion;
        this.selectionReason = selectionReason;
        this.componentId = componentId;
        this.selectedVariantsById = selectedVariants;
        this.selectedVariants = ImmutableList.copyOf(selectedVariants.values());
        this.allVariants = allVariants.isEmpty() ? this.selectedVariants : allVariants;
        this.repositoryName = repositoryName;
    }

    @Override
    public ComponentIdentifier getId() {
        return componentId;
    }

    @Override
    @Deprecated
    public String getRepositoryName() {
        return repositoryName;
    }

    @Nullable
    @Override
    public String getRepositoryId() {
        return repositoryName;
    }

    @Override
    public Set<DependencyResult> getDependencies() {
        // The component's dependencies are strictly a function of the dependencies of its variants.
        // Only calculate this value if necessary.
        if (this.cachedComponentDependencies == null) {
            int size = 0;
            for (ImmutableSet<DependencyResult> dependencies : variantDependencies.values()) {
                size += dependencies.size();
            }
            ImmutableSet.Builder<DependencyResult> builder = ImmutableSet.builderWithExpectedSize(size);
            for (ImmutableSet<DependencyResult> dependencies : variantDependencies.values()) {
                builder.addAll(dependencies);
            }
            this.cachedComponentDependencies = builder.build();
        }
        return this.cachedComponentDependencies;
    }

    @Override
    public Set<ResolvedDependencyResult> getDependents() {
        return Collections.unmodifiableSet(dependents);
    }

    public DefaultResolvedComponentResult addDependent(ResolvedDependencyResult dependent) {
        this.dependents.add(dependent);
        return this;
    }

    @Override
    public ComponentSelectionReason getSelectionReason() {
        return selectionReason;
    }

    @Override
    @Nullable
    public ModuleVersionIdentifier getModuleVersion() {
        return moduleVersion;
    }

    @Override
    public String toString() {
        return getId().getDisplayName();
    }

    @Override
    public List<ResolvedVariantResult> getVariants() {
        return selectedVariants;
    }

    @Override
    public List<ResolvedVariantResult> getAvailableVariants() {
        return allVariants;
    }

    @Override
    public List<DependencyResult> getDependenciesForVariant(ResolvedVariantResult variant) {
        if (!selectedVariants.contains(variant)) {
            reportInvalidVariant(variant);
        }
        return ImmutableList.copyOf(variantDependencies.getOrDefault(variant, ImmutableSet.of()));
    }

    private void reportInvalidVariant(ResolvedVariantResult variant) {
        Optional<ResolvedVariantResult> sameName = selectedVariants.stream()
            .filter(v -> v.getDisplayName().equals(variant.getDisplayName()))
            .findFirst();
        String moreInfo = sameName.isPresent()
            ? "A variant with the same name exists but is not the same instance."
            : "There's no resolved variant with the same name.";
        throw new InvalidUserCodeException("Variant '" + variant.getDisplayName() + "' doesn't belong to resolved component '" + this + "'. " + moreInfo + " Most likely you are using a variant from another component to get the dependencies of this component.");
    }

    @Override
    @Nullable
    public ResolvedVariantResult getVariant(long id) {
        return selectedVariantsById.get(id);
    }

    public void setVariantDependencies(ResolvedVariantResult variant, ImmutableSet<DependencyResult> dependencies) {
        this.variantDependencies.put(variant, dependencies);
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

    /**
     * Finalize this component, making it immutable and ensuring its contents are stored in memory-efficient data structures.
     */
    public void complete() {
        this.dependents = ImmutableSet.copyOf(dependents);
        this.variantDependencies = ImmutableMap.copyOf(variantDependencies);
    }
}
