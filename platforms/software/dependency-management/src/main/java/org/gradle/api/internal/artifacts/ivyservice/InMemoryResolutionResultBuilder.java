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
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultGraphBuilder;
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult;
import org.gradle.api.internal.artifacts.result.ResolvedComponentResultInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.Collections;

/**
 * Dependency graph visitor that will build a {@link ResolutionResult} eagerly.
 * It is designed to be used during resolution for build dependencies.
 */
public class InMemoryResolutionResultBuilder implements DependencyGraphVisitor {

    private final ResolutionResultGraphBuilder resolutionResultBuilder = new ResolutionResultGraphBuilder();
    private final boolean includeAllSelectableVariantResults;

    private long rootVariantId;
    private long rootComponentId;
    private ImmutableAttributes requestAttributes;

    public InMemoryResolutionResultBuilder(boolean includeAllSelectableVariantResults) {
        this.includeAllSelectableVariantResults = includeAllSelectableVariantResults;
    }

    @Override
    public void start(RootGraphNode root) {
        this.rootVariantId = root.getNodeId();
        this.rootComponentId = root.getOwner().getResultId();
        this.requestAttributes = root.getResolveState().getAttributes();
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        DependencyGraphComponent component = node.getOwner();
        resolutionResultBuilder.startVisitComponent(component.getResultId(), component.getSelectionReason(), component.getRepositoryName());
        resolutionResultBuilder.visitComponentDetails(component.getComponentId(), component.getModuleVersion());
        for (ResolvedGraphVariant variant : component.getSelectedVariants()) {
            ResolvedVariantResult publicView = component.getResolveState().getPublicViewFor(variant.getResolveState(), null);
            resolutionResultBuilder.visitSelectedVariant(variant.getNodeId(), publicView);
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
        resolutionResultBuilder.visitOutgoingEdges(node.getOwner().getResultId(), node.getOutgoingEdges());
    }

    public MinimalResolutionResult getResolutionResult() {
        if (requestAttributes == null) {
            throw new IllegalStateException("Resolution result not computed yet");
        }
        ResolvedComponentResultInternal root = resolutionResultBuilder.getRoot(rootComponentId);
        return new MinimalResolutionResult(rootVariantId, () -> root, requestAttributes);
    }
}
