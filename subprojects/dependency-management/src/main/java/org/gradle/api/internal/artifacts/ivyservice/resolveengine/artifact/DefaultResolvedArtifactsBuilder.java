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
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Maps.newLinkedHashMap;
import static com.google.common.collect.Sets.newLinkedHashSet;

/**
 * Collects all artifacts and their build dependencies.
 */
public class DefaultResolvedArtifactsBuilder implements DependencyArtifactsVisitor {
    private final boolean buildProjectDependencies;
    private final Map<Long, ArtifactSet> artifactSets = newLinkedHashMap();
    private final Set<ArtifactSet> buildableArtifactSets = new HashSet<ArtifactSet>();

    public DefaultResolvedArtifactsBuilder(boolean buildProjectDependencies) {
        this.buildProjectDependencies = buildProjectDependencies;
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, ArtifactSet artifacts) {
        artifactSets.put(artifacts.getId(), artifacts);

        // Don't collect build dependencies if not required
        if (!buildProjectDependencies) {
            return;
        }
        if (buildableArtifactSets.contains(artifacts)) {
            return;
        }

        // Collect the build dependencies in 2 steps: collect the artifact sets while traversing and at the end of traversal unpack the build dependencies for each
        // We need to discard the artifact sets to avoid keeping strong references

        ConfigurationMetadata configurationMetadata = to.getMetadata();
        if (!(configurationMetadata instanceof LocalConfigurationMetadata)) {
            return;
        }

        if (from.getOwner().getComponentId() instanceof ProjectComponentIdentifier) {
            // This is here to attempt to leave out build dependencies that would cause a cycle in the task graph for the current build, so that the cross-build cycle detection kicks in. It's not fully correct
            ProjectComponentIdentifier incomingId = (ProjectComponentIdentifier) from.getOwner().getComponentId();
            if (!incomingId.getBuild().isCurrentBuild()) {
                return;
            }
        }

        buildableArtifactSets.add(artifacts);
    }

    @Override
    public void finishArtifacts() {
    }

    public VisitedArtifactsResults complete() {
        Set<ResolvedArtifactSet> allArtifactSets = newLinkedHashSet();
        Map<Long, ResolvedArtifactSet> resolvedArtifactsById = newLinkedHashMap();

        for (Map.Entry<Long, ArtifactSet> entry : artifactSets.entrySet()) {
            ResolvedArtifactSet resolvedArtifacts = entry.getValue().getArtifacts();
            if (!buildableArtifactSets.contains(entry.getValue())) {
                resolvedArtifacts = NoBuildDependenciesArtifactSet.of(resolvedArtifacts);
            }
            allArtifactSets.add(resolvedArtifacts);
            resolvedArtifactsById.put(entry.getKey(), resolvedArtifacts);
        }
        ResolvedArtifactSet allArtifacts = CompositeArtifactSet.of(allArtifactSets);

        return new DefaultResolvedArtifactResults(allArtifacts, resolvedArtifactsById);
    }
}
