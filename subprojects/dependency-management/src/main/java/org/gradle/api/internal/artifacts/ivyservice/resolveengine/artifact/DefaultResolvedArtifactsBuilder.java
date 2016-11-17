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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Collects all artifacts and their build dependencies.
 */
public class DefaultResolvedArtifactsBuilder implements DependencyArtifactsVisitor, VisitedArtifactsResults {
    private final DefaultResolvedArtifactResults artifactResults = new DefaultResolvedArtifactResults();
    private final boolean buildProjectDependencies;
    private Set<LocalConfigurationMetadata> requiredNodes = new HashSet<LocalConfigurationMetadata>();
    private List<TaskDependency> buildDependencies;

    public DefaultResolvedArtifactsBuilder(boolean buildProjectDependencies) {
        this.buildProjectDependencies = buildProjectDependencies;
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, ArtifactSet artifacts) {
        artifactResults.addArtifactSet(artifacts);

        // Don't collect build dependencies if not required
        if (!buildProjectDependencies) {
            return;
        }

        // Collect the build dependencies in 2 steps: collect the nodes while traversing and at the end of traversal unpack the build dependencies for each
        // We need to discard the node to avoid keeping strong references
        // This is a migration step to move the knowledge of the build dependencies into the artifact set

        ConfigurationMetadata configurationMetadata = to.getMetadata();
        if (!(configurationMetadata instanceof LocalConfigurationMetadata) || requiredNodes.contains(configurationMetadata)) {
            return;
        }

        LocalConfigurationMetadata localConfigurationMetadata = (LocalConfigurationMetadata) configurationMetadata;
        if (from.getOwner().getComponentId() instanceof ProjectComponentIdentifier) {
            // This is here to attempt to leave out build dependencies that would cause a cycle in the task graph for the current build, so that the cross-build cycle detection kicks in. It's not fully correct
            ProjectComponentIdentifier incomingId = (ProjectComponentIdentifier) from.getOwner().getComponentId();
            if (!incomingId.getBuild().isCurrentBuild()) {
                return;
            }
        }

        requiredNodes.add(localConfigurationMetadata);
    }

    @Override
    public void finishArtifacts() {
        buildDependencies = new ArrayList<TaskDependency>(requiredNodes.size());
        for (LocalConfigurationMetadata metadata : requiredNodes) {
            buildDependencies.add(metadata.getArtifactBuildDependencies());
        }
        requiredNodes.clear();
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        dest.addAll(buildDependencies);
    }

    public ResolvedArtifactResults resolve() {
        artifactResults.resolveNow();
        return artifactResults;
    }
}
