/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.UnresolvedDependency;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultGraphBuilder;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dependency graph visitor that will build a {@link ResolutionResult} eagerly.
 */
public class InMemoryResolutionResultBuilder implements ResolutionResultGraphVisitor {

    private final ResolutionResultGraphBuilder resolutionResultBuilder = new ResolutionResultGraphBuilder();
    private final boolean includeAllSelectableVariantResults;

    private boolean resultComputed;
    private long rootComponentId;
    private ImmutableAttributes requestAttributes;
    private boolean mayHaveVirtualPlatforms;

    public InMemoryResolutionResultBuilder(boolean includeAllSelectableVariantResults) {
        this.includeAllSelectableVariantResults = includeAllSelectableVariantResults;
    }

    @Override
    public void start(RootGraphNode root) {
        mayHaveVirtualPlatforms = root.getResolveOptimizations().mayHaveVirtualPlatforms();
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        DependencyGraphComponent component = node.getOwner();
        resolutionResultBuilder.startVisitComponent(component.getResultId(), component.getSelectionReason(), component.getRepositoryName());
        resolutionResultBuilder.visitComponentDetails(component.getComponentId(), component.getModuleVersion());

        for (ResolvedGraphVariant variant : component.getSelectedVariants()) {
            ResolvedGraphVariant externalVariant = variant.getExternalVariant();
            ResolvedVariantResult externalVariantResult = externalVariant != null ? externalVariant.getResolveState().getVariantResult(null) : null;
            resolutionResultBuilder.visitSelectedVariant(variant.getNodeId(), variant.getResolveState().getVariantResult(externalVariantResult));
        }

        if (includeAllSelectableVariantResults) {
            resolutionResultBuilder.visitComponentVariants(component.getResolveState().getAllSelectableVariantResults());
        } else {
            resolutionResultBuilder.visitComponentVariants(Collections.emptyList());
        }

        resolutionResultBuilder.endVisitComponent();
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        final Collection<? extends DependencyGraphEdge> dependencies = mayHaveVirtualPlatforms
            ? node.getOutgoingEdges().stream()
            .filter(dep -> !dep.isTargetVirtualPlatform())
            .collect(Collectors.toList())
            : node.getOutgoingEdges();
        resolutionResultBuilder.visitOutgoingEdges(node.getOwner().getResultId(), dependencies);
    }

    @Override
    public void finish(RootGraphNode root) {
        this.rootComponentId = root.getOwner().getResultId();
        this.requestAttributes = root.getResolveState().getAttributes();
    }

    @Override
    public MinimalResolutionResult getResolutionResult(Set<UnresolvedDependency> dependencyLockingFailures) {
        if (requestAttributes == null) {
            throw new IllegalStateException("Resolution result not computed yet");
        }

        if (resultComputed) {
            throw new IllegalStateException("Resolution result already computed");
        }
        resultComputed = true;

        resolutionResultBuilder.addDependencyLockingFailures(rootComponentId, dependencyLockingFailures);
        ResolvedComponentResult rootComponent = resolutionResultBuilder.getRoot(rootComponentId);
        return new MinimalResolutionResult(() -> rootComponent, requestAttributes);
    }
}
