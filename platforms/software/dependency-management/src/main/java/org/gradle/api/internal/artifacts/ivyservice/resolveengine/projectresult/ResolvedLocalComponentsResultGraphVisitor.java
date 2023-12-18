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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode;

import java.util.ArrayList;
import java.util.List;

public class ResolvedLocalComponentsResultGraphVisitor implements DependencyGraphVisitor, ResolvedLocalComponentsResult {
    private final List<ResolvedProjectConfiguration> resolvedProjectConfigurations = new ArrayList<>();
    private final BuildIdentifier thisBuild;
    private ComponentIdentifier rootId;

    public ResolvedLocalComponentsResultGraphVisitor(BuildIdentifier thisBuild) {
        this.thisBuild = thisBuild;
    }

    @Override
    public void start(RootGraphNode root) {
        this.rootId = root.getOwner().getComponentId();
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        ComponentIdentifier componentId = node.getOwner().getComponentId();
        if (!rootId.equals(componentId) && componentId instanceof ProjectComponentIdentifier) {
            ProjectComponentIdentifier projectComponentId = (ProjectComponentIdentifier) componentId;
            if (projectComponentId.getBuild().equals(thisBuild)) {
                resolvedProjectConfigurations.add(new DefaultResolvedProjectConfiguration(projectComponentId, node.getResolvedConfigurationId().getConfiguration()));
            }
        }
    }

    @Override
    public Iterable<ResolvedProjectConfiguration> getResolvedProjectConfigurations() {
        return resolvedProjectConfigurations;
    }

    public ResolvedLocalComponentsResult complete() {
        return this;
    }
}
