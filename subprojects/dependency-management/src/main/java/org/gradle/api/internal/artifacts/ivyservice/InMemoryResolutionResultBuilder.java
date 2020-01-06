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
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DefaultResolutionResultBuilder;

/**
 * Dependency graph visitor that will build a {@link ResolutionResult} eagerly.
 * It is designed to be used during resolution for build dependencies.
 *
 * @see DefaultConfigurationResolver#resolveBuildDependencies(ConfigurationInternal, org.gradle.api.internal.artifacts.ResolverResults)
 */
public class InMemoryResolutionResultBuilder implements DependencyGraphVisitor {

    private final DefaultResolutionResultBuilder resolutionResultBuilder = new DefaultResolutionResultBuilder();
    private ResolutionResult resolutionResult;

    @Override
    public void start(RootGraphNode root) {
        resolutionResultBuilder.setRequestedAttributes(root.getMetadata().getAttributes());
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        resolutionResultBuilder.visitComponent(node.getOwner());
    }

    @Override
    public void visitSelector(DependencyGraphSelector selector) {

    }

    @Override
    public void visitEdges(DependencyGraphNode node) {
        resolutionResultBuilder.visitOutgoingEdges(node.getOwner().getResultId(), node.getOutgoingEdges());
    }

    @Override
    public void finish(DependencyGraphNode root) {
        resolutionResult = resolutionResultBuilder.complete(root.getOwner().getResultId());
    }

    public ResolutionResult getResolutionResult() {
        if (resolutionResult == null) {
            throw new IllegalStateException("Resolution result not computed yet");
        }
        return resolutionResult;
    }
}
