/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

class TransformingAsyncArtifactListener implements ResolvedArtifactSet.AsyncArtifactListener {
    private final Map<ComponentArtifactIdentifier, TransformationResult> artifactResults;
    private final TransformationNodeRegistry transformationNodeRegistry;
    private final Transformation transformation;
    private final TransformQueue queue;

    TransformingAsyncArtifactListener(
        Transformation transformation,
        BuildOperationQueue<RunnableBuildOperation> actions,
        Map<ComponentArtifactIdentifier, TransformationResult> artifactResults,
        ExecutionGraphDependenciesResolver dependenciesResolver,
        TransformationNodeRegistry transformationNodeRegistry,
        @Nullable NodeExecutionContext nodeExecutionContext,
        boolean isEntryPoint
    ) {
        this.queue = new TransformQueue(transformation, actions, artifactResults, dependenciesResolver, nodeExecutionContext, isEntryPoint);
        this.artifactResults = artifactResults;
        this.transformation = transformation;
        this.transformationNodeRegistry = transformationNodeRegistry;
    }

    @Override
    public void artifactAvailable(ResolvableArtifact artifact) {
        ComponentArtifactIdentifier artifactId = artifact.getId();
        Optional<TransformationNode> node = transformationNodeRegistry.getIfExecuted(artifactId, transformation);
        if (node.isPresent()) {
            artifactResults.put(artifactId, new PrecomputedTransformationResult(node.get().getTransformedSubject()));
        } else {
            queue.artifactAvailable(artifact);
        }
    }

    @Override
    public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        // Visit everything
        return FileCollectionStructureVisitor.VisitType.Visit;
    }

    @Override
    public boolean requireArtifactFiles() {
        // Always need the files, as we need to run the transform in order to calculate the output artifacts.
        return true;
    }
}
