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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionCause;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphDependency;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult;
import org.gradle.internal.Describables;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultResolutionResultBuilder implements ResolvedComponentVisitor {
    private static final DefaultComponentSelectionDescriptor DEPENDENCY_LOCKING = new DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONSTRAINT, Describables.of("Dependency locking"));
    private final Map<Long, DefaultResolvedComponentResult> components = new HashMap<>();
    private final CachingDependencyResultFactory dependencyResultFactory = new CachingDependencyResultFactory();
    private AttributeContainer requestedAttributes;
    private Long id;
    private ComponentSelectionReason selectionReason;
    private ComponentIdentifier componentId;
    private ModuleVersionIdentifier moduleVersion;
    private String repoId;

    public static ResolutionResult empty(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier, AttributeContainer attributes) {
        DefaultResolutionResultBuilder builder = new DefaultResolutionResultBuilder();
        builder.setRequestedAttributes(attributes);
        builder.startVisitComponent(0L, ComponentSelectionReasons.root());
        builder.visitComponentDetails(componentIdentifier, id, null);
        builder.visitComponentVariants(Collections.emptyList(), Collections.emptyList());
        return builder.complete(0L);
    }

    public void setRequestedAttributes(AttributeContainer attributes) {
        requestedAttributes = attributes;
    }

    public ResolutionResult complete(Long rootId) {
        return new DefaultResolutionResult(new RootFactory(components.get(rootId)), requestedAttributes);
    }

    @Override
    public void startVisitComponent(Long id, ComponentSelectionReason selectionReason) {
        this.id = id;
        this.selectionReason = selectionReason;
    }

    @Override
    public void visitComponentDetails(ComponentIdentifier componentId, ModuleVersionIdentifier moduleVersion, @Nullable String repoId) {
        this.componentId = componentId;
        this.moduleVersion = moduleVersion;
        this.repoId = repoId;
    }

    @Override
    public void visitComponentVariants(List<ResolvedVariantResult> selectedVariants, List<ResolvedVariantResult> allVariants) {
        // The nodes in the graph represent variants (mostly), so a given component may be visited multiple times
        if (!components.containsKey(id)) {
            components.put(id, new DefaultResolvedComponentResult(moduleVersion, selectionReason, componentId, selectedVariants, allVariants, repoName));
        }
    }

    public void visitOutgoingEdges(Long fromComponent, Collection<? extends ResolvedGraphDependency> dependencies) {
        DefaultResolvedComponentResult from = components.get(fromComponent);
        for (ResolvedGraphDependency d : dependencies) {
            DependencyResult dependencyResult;
            ResolvedVariantResult fromVariant = d.getFromVariant();
            if (d.getFailure() != null) {
                dependencyResult = dependencyResultFactory.createUnresolvedDependency(d.getRequested(), from, d.isConstraint(), d.getReason(), d.getFailure());
            } else {
                DefaultResolvedComponentResult selected = components.get(d.getSelected());
                if (selected == null) {
                    throw new IllegalStateException("Corrupt serialized resolution result. Cannot find selected module (" + d.getSelected() + ") for " + (d.isConstraint() ? "constraint " : "") + fromVariant + " -> " + d.getRequested().getDisplayName());
                }
                dependencyResult = dependencyResultFactory.createResolvedDependency(d.getRequested(), from, selected, d.getSelectedVariant(), d.isConstraint());
                selected.addDependent((ResolvedDependencyResult) dependencyResult);
            }
            from.addDependency(dependencyResult);
            if (fromVariant != null) {
                from.associateDependencyToVariant(dependencyResult, fromVariant);
            }
        }
    }

    public void addExtraFailures(Long rootId, Set<UnresolvedDependency> extraFailures) {
        DefaultResolvedComponentResult root = components.get(rootId);
        for (UnresolvedDependency failure : extraFailures) {
            ModuleVersionSelector failureSelector = failure.getSelector();
            ModuleComponentSelector failureComponentSelector = DefaultModuleComponentSelector.newSelector(failureSelector.getModule(), failureSelector.getVersion());
            root.addDependency(dependencyResultFactory.createUnresolvedDependency(failureComponentSelector, root, true,
                    ComponentSelectionReasons.of(DEPENDENCY_LOCKING),
                new ModuleVersionResolveException(failureComponentSelector, () -> "Dependency lock state out of date", failure.getProblem())));
        }
    }

    private static class RootFactory implements Factory<ResolvedComponentResult> {
        private final DefaultResolvedComponentResult rootModule;

        public RootFactory(DefaultResolvedComponentResult rootModule) {
            this.rootModule = rootModule;
        }

        @Override
        public ResolvedComponentResult create() {
            return rootModule;
        }
    }
}
