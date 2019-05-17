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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphDependency;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedVariantDetails;
import org.gradle.api.internal.artifacts.result.DefaultResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedComponentResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.internal.Describables;
import org.gradle.internal.Factory;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultResolutionResultBuilder {
    private final Map<Long, DefaultResolvedComponentResult> modules = new HashMap<Long, DefaultResolvedComponentResult>();
    private final CachingDependencyResultFactory dependencyResultFactory = new CachingDependencyResultFactory();

    public static ResolutionResult empty(ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier) {
        DefaultResolutionResultBuilder builder = new DefaultResolutionResultBuilder();
        builder.visitComponent(new DetachedComponentResult(0L, id, ComponentSelectionReasons.root(), componentIdentifier, Collections.emptyList(), null));
        return builder.complete(0L);
    }

    public ResolutionResult complete(Long rootId) {
        return new DefaultResolutionResult(new RootFactory(modules.get(rootId)));
    }

    public void visitComponent(ResolvedGraphComponent component) {
        create(component.getResultId(), component.getModuleVersion(), component.getSelectionReason(), component.getComponentId(), variantDetails(component), component.getRepositoryName());
    }

    private static List<ResolvedVariantResult> variantDetails(ResolvedGraphComponent component) {
        List<ResolvedVariantDetails> resolvedVariants = component.getResolvedVariants();
        ImmutableList.Builder<ResolvedVariantResult> builder = ImmutableList.builderWithExpectedSize(resolvedVariants.size());
        for (ResolvedVariantDetails resolvedVariant : resolvedVariants) {
            builder.add(new DefaultResolvedVariantResult(resolvedVariant.getVariantName(), resolvedVariant.getVariantAttributes(), resolvedVariant.getCapabilities()));
        }
        return builder.build();
    }

    public void visitOutgoingEdges(Long fromComponent, Collection<? extends ResolvedGraphDependency> dependencies) {
        DefaultResolvedComponentResult from = modules.get(fromComponent);
        for (ResolvedGraphDependency d : dependencies) {
            DependencyResult dependencyResult;
            if (d.getFailure() != null) {
                dependencyResult = dependencyResultFactory.createUnresolvedDependency(d.getRequested(), from, d.isConstraint(), d.getReason(), d.getFailure());
            } else {
                DefaultResolvedComponentResult selected = modules.get(d.getSelected());
                dependencyResult = dependencyResultFactory.createResolvedDependency(d.getRequested(), from, selected, d.isConstraint());
                selected.addDependent((ResolvedDependencyResult) dependencyResult);
            }
            from.addDependency(dependencyResult);
        }
    }

    private void create(Long id, ModuleVersionIdentifier moduleVersion, ComponentSelectionReason selectionReason, ComponentIdentifier componentId, List<ResolvedVariantResult> variants, String repoName) {
        if (!modules.containsKey(id)) {
            modules.put(id, new DefaultResolvedComponentResult(moduleVersion, selectionReason, componentId, variants, repoName));
        }
    }

    public void addExtraFailures(Long rootId, Set<UnresolvedDependency> extraFailures) {
        DefaultResolvedComponentResult root = modules.get(rootId);
        for (UnresolvedDependency failure : extraFailures) {
            ModuleVersionSelector failureSelector = failure.getSelector();
            ModuleComponentSelector failureComponentSelector = DefaultModuleComponentSelector.newSelector(failureSelector.getModule(), failureSelector.getVersion());
            root.addDependency(dependencyResultFactory.createUnresolvedDependency(failureComponentSelector, root, true,
                    ComponentSelectionReasons.of(new DefaultComponentSelectionDescriptor(ComponentSelectionCause.CONSTRAINT, Describables.of("Dependency locking"))),
                new ModuleVersionResolveException(failureComponentSelector, () -> "Dependency lock state out of date", failure.getProblem())));
        }
    }

    private static class RootFactory implements Factory<ResolvedComponentResult> {
        private DefaultResolvedComponentResult rootModule;

        public RootFactory(DefaultResolvedComponentResult rootModule) {
            this.rootModule = rootModule;
        }

        @Override
        public ResolvedComponentResult create() {
            return rootModule;
        }
    }
}
