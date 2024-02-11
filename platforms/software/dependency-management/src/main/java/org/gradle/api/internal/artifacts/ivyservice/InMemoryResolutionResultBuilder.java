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

import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DefaultResolutionResultBuilder;
import org.gradle.api.internal.artifacts.result.DefaultMinimalResolutionResult;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.attributes.ImmutableAttributes;

/**
 * Dependency graph visitor that will build a {@link ResolutionResult} eagerly.
 * It is designed to be used during resolution for build dependencies.
 *
 * @see DefaultConfigurationResolver#resolveBuildDependencies(ResolveContext)
 */
public class InMemoryResolutionResultBuilder implements DependencyGraphVisitor {

    private final DefaultResolutionResultBuilder resolutionResultBuilder = new DefaultResolutionResultBuilder();
    private ResolvedComponentResult root;
    private ImmutableAttributes requestAttributes;

    @Override
    public void visitNode(DependencyGraphNode node) {
        DependencyGraphComponent component = node.getOwner();
        resolutionResultBuilder.startVisitComponent(component.getResultId(), component.getSelectionReason(), component.getRepositoryName());
        resolutionResultBuilder.visitComponentDetails(component.getComponentId(), component.getModuleVersion());
        for (ResolvedGraphVariant variant : component.getSelectedVariants()) {
            resolutionResultBuilder.visitSelectedVariant(variant.getNodeId(), variant.getResolveState().getVariantResult(null));
        }
        resolutionResultBuilder.visitComponentVariants(component.getResolveState().getAllSelectableVariantResults());
        resolutionResultBuilder.endVisitComponent();
    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        resolutionResultBuilder.visitOutgoingEdges(node.getOwner().getResultId(), node.getOutgoingEdges());
    }

    @Override
    public void finish(RootGraphNode root) {
        Long resultId = root.getOwner().getResultId();
        this.root = resolutionResultBuilder.getRoot(resultId);
        this.requestAttributes = root.getResolveState().getAttributes();
    }

    public MinimalResolutionResult getResolutionResult() {
        if (requestAttributes == null) {
            throw new IllegalStateException("Resolution result not computed yet");
        }
        return new DefaultMinimalResolutionResult(() -> root, requestAttributes);
    }
}
