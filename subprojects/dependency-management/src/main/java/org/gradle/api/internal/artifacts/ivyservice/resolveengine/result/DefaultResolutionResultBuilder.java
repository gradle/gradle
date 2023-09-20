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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphDependency;
import org.gradle.api.internal.artifacts.result.DefaultMinimalResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultResolutionResultBuilder implements ResolvedComponentVisitor {
    private static final DefaultComponentSelectionDescriptor DEPENDENCY_LOCKING = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONSTRAINT, Describables.of("Dependency locking"));
    private final Long2ObjectMap<DefaultResolvedComponentResult> components = new Long2ObjectOpenHashMap<>();
    private final CachingDependencyResultFactory dependencyResultFactory = new CachingDependencyResultFactory();
    private long id;
    private ComponentSelectionReason selectionReason;
    private ComponentIdentifier componentId;
    private ModuleVersionIdentifier moduleVersion;
    private String repoName;
    private ImmutableList<ResolvedVariantResult> allVariants;
    private final Map<Long, ResolvedVariantResult> selectedVariants = new LinkedHashMap<>();

    public static MinimalResolutionResult empty(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier, AttributeContainer attributes) {
        DefaultResolutionResultBuilder builder = new DefaultResolutionResultBuilder();
        builder.startVisitComponent(0L, ComponentSelectionReasons.root(), null);
        builder.visitComponentDetails(componentIdentifier, id);
        builder.visitComponentVariants(Collections.emptyList());
        builder.endVisitComponent();
        ResolvedComponentResult root = builder.getRoot(0L);
        return new DefaultMinimalResolutionResult(() -> root, attributes);
    }

    public ResolvedComponentResult getRoot(long rootId) {
        return components.get(rootId);
    }

    @Override
    public void startVisitComponent(Long id, ComponentSelectionReason selectionReason, @Nullable String repoName) {
        this.id = id;
        this.selectionReason = selectionReason;
        this.selectedVariants.clear();
        this.allVariants = null;
        this.repoName = repoName;
    }

    @Override
    public void visitComponentDetails(ComponentIdentifier componentId, ModuleVersionIdentifier moduleVersion) {
        this.componentId = componentId;
        this.moduleVersion = moduleVersion;
    }

    @Override
    public void visitSelectedVariant(Long id, ResolvedVariantResult variant) {
        selectedVariants.put(id, variant);
    }

    @Override
    public void visitComponentVariants(List<ResolvedVariantResult> allVariants) {
        this.allVariants = ImmutableList.copyOf(allVariants);
    }

    @Override
    public void endVisitComponent() {
        // The nodes in the graph represent variants (mostly) and multiple variants of a component may be included in the graph, so a given component may be visited multiple times
        if (!components.containsKey(id)) {
            components.put(id, new DefaultResolvedComponentResult(moduleVersion, selectionReason, componentId, ImmutableMap.copyOf(selectedVariants), allVariants, repoName));
        }
        selectedVariants.clear();
        allVariants = null;
    }

    public void visitOutgoingEdges(long fromComponentId, Collection<? extends ResolvedGraphDependency> dependencies) {
        DefaultResolvedComponentResult fromComponent = components.get(fromComponentId);
        for (ResolvedGraphDependency d : dependencies) {
            DependencyResult dependencyResult;
            ResolvedVariantResult fromVariant = fromComponent.getVariant(d.getFromVariant());
            if (fromVariant == null) {
                throw new IllegalStateException("Corrupt serialized resolution result. Cannot find variant (" + d.getFromVariant() + ") for " + (d.isConstraint() ? "constraint " : "") + fromComponent + " -> " + d.getRequested().getDisplayName());
            }
            if (d.getFailure() != null) {
                dependencyResult = dependencyResultFactory.createUnresolvedDependency(d.getRequested(), fromComponent, d.isConstraint(), d.getReason(), d.getFailure());
            } else {
                DefaultResolvedComponentResult selectedComponent = components.get(d.getSelected().longValue());
                if (selectedComponent == null) {
                    throw new IllegalStateException("Corrupt serialized resolution result. Cannot find selected component (" + d.getSelected() + ") for " + (d.isConstraint() ? "constraint " : "") + fromVariant + " -> " + d.getRequested().getDisplayName());
                }
                ResolvedVariantResult selectedVariant;
                if (d.getSelectedVariant() != null) {
                    selectedVariant = selectedComponent.getVariant(d.getSelectedVariant());
                    if (selectedVariant == null) {
                        throw new IllegalStateException("Corrupt serialized resolution result. Cannot find selected variant (" + d.getSelectedVariant() + ") for " + (d.isConstraint() ? "constraint " : "") + fromVariant + " -> " + d.getRequested().getDisplayName());
                    }
                } else {
                    selectedVariant = null;
                }
                dependencyResult = dependencyResultFactory.createResolvedDependency(d.getRequested(), fromComponent, selectedComponent, selectedVariant, d.isConstraint());
                selectedComponent.addDependent((ResolvedDependencyResult) dependencyResult);
            }
            fromComponent.addDependency(dependencyResult);
            fromComponent.associateDependencyToVariant(dependencyResult, fromVariant);
        }
    }

    // TODO: Dependency locking failures should be attached to the resolution result just like
    // dependency verification failures are. Dependency locking failures are not unresolved dependencies
    // and should not be modeled as one.
    public void addDependencyLockingFailures(long rootId, Set<UnresolvedDependency> extraFailures) {
        DefaultResolvedComponentResult root = components.get(rootId);
        for (UnresolvedDependency failure : extraFailures) {
            ModuleVersionSelector failureSelector = failure.getSelector();
            ModuleComponentSelector failureComponentSelector = DefaultModuleComponentSelector.newSelector(failureSelector.getModule(), failureSelector.getVersion());
            root.addDependency(dependencyResultFactory.createUnresolvedDependency(failureComponentSelector, root, true,
                ComponentSelectionReasons.of(DEPENDENCY_LOCKING),
                new ModuleVersionResolveException(failureComponentSelector, () -> "Dependency lock state out of date", failure.getProblem())));
        }
    }
}
