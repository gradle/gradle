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

import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects all artifacts and their build dependencies.
 */
public class DefaultResolvedArtifactsBuilder implements DependencyArtifactsVisitor {
    private final boolean buildProjectDependencies;
    private final ResolutionStrategy.SortOrder sortOrder;
    private final List<ArtifactSet> artifactSetsById = new ArrayList<>();

    public DefaultResolvedArtifactsBuilder(boolean buildProjectDependencies, ResolutionStrategy.SortOrder sortOrder) {
        this.buildProjectDependencies = buildProjectDependencies;
        this.sortOrder = sortOrder;
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, LocalFileDependencyMetadata fileDependency, int artifactSetId, ArtifactSet artifacts) {
        collectArtifacts(artifactSetId, artifacts);
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, int artifactSetId, ArtifactSet artifacts) {
        // Don't collect build dependencies if not required
        if (!buildProjectDependencies) {
            artifacts = new NoBuildDependenciesArtifactSet(artifacts);
        }
        collectArtifacts(artifactSetId, artifacts);
    }

    private void collectArtifacts(int artifactSetId, ArtifactSet artifacts) {
        // Collect artifact sets in a list, using the id of the set as its index in the list
        assert artifactSetsById.size() >= artifactSetId;
        if (artifactSetsById.size() == artifactSetId) {
            artifactSetsById.add(artifacts);
        }
    }

    public VisitedArtifactsResults complete() {
        return new DefaultVisitedArtifactResults(sortOrder, artifactSetsById);
    }
}
