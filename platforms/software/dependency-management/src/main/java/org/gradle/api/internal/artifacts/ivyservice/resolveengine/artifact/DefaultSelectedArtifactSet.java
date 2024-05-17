/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.internal.artifacts.configurations.ResolutionHost;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

/**
 * Resolves a {@link ResolvedArtifactSet} into a {@link SelectedArtifactSet}.
 */
public class DefaultSelectedArtifactSet implements SelectedArtifactSet {
    private final VisitedGraphResults graphResults;
    private final ResolvedArtifactSet resolvedArtifacts;
    private final ResolvedArtifactSetResolver artifactResolver;
    private final ResolutionHost resolutionHost;

    public DefaultSelectedArtifactSet(
        ResolvedArtifactSetResolver artifactResolver,
        VisitedGraphResults graphResults,
        ResolvedArtifactSet resolvedArtifacts,
        ResolutionHost resolutionHost
    ) {
        this.artifactResolver = artifactResolver;
        this.graphResults = graphResults;
        this.resolvedArtifacts = resolvedArtifacts;
        this.resolutionHost = resolutionHost;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        graphResults.visitFailures(context::visitFailure);
        context.add(resolvedArtifacts);
    }

    @Override
    public void visitArtifacts(ArtifactVisitor visitor, boolean continueOnSelectionFailure) {
        if (graphResults.hasAnyFailure()) {
            graphResults.visitFailures(visitor::visitFailure);
            if (!continueOnSelectionFailure) {
                return;
            }
        }

        artifactResolver.visitInUnmanagedWorkerThread(resolvedArtifacts, visitor, resolutionHost);
    }
}
