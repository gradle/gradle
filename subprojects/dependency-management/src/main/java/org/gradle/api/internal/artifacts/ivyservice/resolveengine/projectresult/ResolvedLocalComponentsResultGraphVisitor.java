/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.projectresult;

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ResolvedLocalComponentsResultGraphVisitor implements DependencyGraphVisitor, ResolvedLocalComponentsResult {
    private final List<ResolvedProjectConfiguration> resolvedProjectConfigurations = new ArrayList<ResolvedProjectConfiguration>();
    private final List<TaskDependency> componentBuildDependencies = new ArrayList<TaskDependency>();
    private final boolean buildProjectDependencies;
    private ComponentIdentifier rootId;

    public ResolvedLocalComponentsResultGraphVisitor(boolean buildProjectDependencies) {
        this.buildProjectDependencies = buildProjectDependencies;
    }

    @Override
    public void start(DependencyGraphNode root) {
        this.rootId = root.getOwner().getComponentId();
    }

    @Override
    public void visitNode(DependencyGraphNode resolvedConfiguration) {
        ComponentIdentifier componentId = resolvedConfiguration.getOwner().getComponentId();
        if (!rootId.equals(componentId) && componentId instanceof ProjectComponentIdentifier) {
            resolvedProjectConfigurations.add(new DefaultResolvedProjectConfiguration((ProjectComponentIdentifier) componentId, resolvedConfiguration.getResolvedConfigurationId().getConfiguration()));
        }
    }

    @Override
    public void visitEdge(DependencyGraphNode resolvedConfiguration) {
        if (!buildProjectDependencies) {
            return;
        }

        ConfigurationMetadata configurationMetadata = resolvedConfiguration.getMetadata();
        if (!(configurationMetadata instanceof LocalConfigurationMetadata)) {
            return;
        }

        LocalConfigurationMetadata localConfigurationMetadata = (LocalConfigurationMetadata) configurationMetadata;

        // If there is an incoming edge then include the dependencies to build the artifacts of the node
        for (DependencyGraphEdge edge : resolvedConfiguration.getIncomingEdges()) {
            if (edge.getFrom().getOwner().getComponentId() instanceof ProjectComponentIdentifier) {
                // This is here to attempt to leave out build dependencies that would cause a cycle in the task graph for the current build, so that the cross-build cycle detection kicks in. It's not fully correct
                ProjectComponentIdentifier incomingId = (ProjectComponentIdentifier) edge.getFrom().getOwner().getComponentId();
                if (!incomingId.getBuild().isCurrentBuild()) {
                    continue;
                }
            }
            componentBuildDependencies.add(localConfigurationMetadata.getArtifactBuildDependencies());
            break;
        }
    }

    @Override
    public void finish(DependencyGraphNode root) {
    }

    @Override
    public void collectArtifactBuildDependencies(Collection<? super TaskDependency> dest) {
        dest.addAll(componentBuildDependencies);
    }

    @Override
    public Iterable<ResolvedProjectConfiguration> getResolvedProjectConfigurations() {
        return resolvedProjectConfigurations;
    }

    public ResolvedLocalComponentsResult complete() {
        return this;
    }
}
