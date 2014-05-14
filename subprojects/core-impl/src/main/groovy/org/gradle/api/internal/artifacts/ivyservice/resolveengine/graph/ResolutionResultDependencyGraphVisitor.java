/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultBuilder;

class ResolutionResultDependencyGraphVisitor implements DependencyGraphVisitor {
    private final ResolutionResultBuilder newModelBuilder;

    ResolutionResultDependencyGraphVisitor(ResolutionResultBuilder newModelBuilder) {
        this.newModelBuilder = newModelBuilder;
    }

    public void start(DependencyGraphBuilder.ConfigurationNode root) {
        newModelBuilder.start(root.toId(), root.metaData.getComponent().getComponentId());
    }

    public void visitNode(DependencyGraphBuilder.ConfigurationNode resolvedConfiguration) {
        newModelBuilder.resolvedModuleVersion(resolvedConfiguration.moduleRevision);
    }

    public void visitEdge(DependencyGraphBuilder.ConfigurationNode resolvedConfiguration) {
        newModelBuilder.resolvedConfiguration(resolvedConfiguration.toId(), resolvedConfiguration.outgoingEdges);
    }

    public void finish(DependencyGraphBuilder.ConfigurationNode root) {

    }
}
