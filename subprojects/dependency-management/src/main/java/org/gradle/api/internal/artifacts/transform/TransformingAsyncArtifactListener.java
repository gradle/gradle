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
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Map;
import java.util.Optional;

public class TransformingAsyncArtifactListener implements ResolvedArtifactSet.AsyncArtifactListener {
    private final Map<ComponentArtifactIdentifier, TransformationResult> artifactResults;
    private final ExecutionGraphDependenciesResolver dependenciesResolver;
    private final TransformationNodeRegistry transformationNodeRegistry;
    private final BuildOperationQueue<RunnableBuildOperation> actions;
    private final Transformation transformation;

    public TransformingAsyncArtifactListener(
        Transformation transformation,
        BuildOperationQueue<RunnableBuildOperation> actions,
        Map<ComponentArtifactIdentifier, TransformationResult> artifactResults,
        ExecutionGraphDependenciesResolver dependenciesResolver,
        TransformationNodeRegistry transformationNodeRegistry
    ) {
        this.artifactResults = artifactResults;
        this.actions = actions;
        this.transformation = transformation;
        this.dependenciesResolver = dependenciesResolver;
        this.transformationNodeRegistry = transformationNodeRegistry;
    }

    @Override
    public void artifactAvailable(ResolvableArtifact artifact) {
        ComponentArtifactIdentifier artifactId = artifact.getId();
        Optional<TransformationNode> node = transformationNodeRegistry.getIfExecuted(artifactId, transformation);
        if (node.isPresent()) {
            artifactResults.put(artifactId, new PrecomputedTransformationResult(node.get().getTransformedSubject()));
        } else {
            File file = artifact.getFile();
            TransformationSubject initialSubject = TransformationSubject.initial(artifactId, file);
            TransformationResult result = createTransformationResult(initialSubject);
            artifactResults.put(artifactId, result);
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

    private TransformationResult createTransformationResult(TransformationSubject initialSubject) {
        CacheableInvocation<TransformationSubject> invocation = transformation.createInvocation(initialSubject, dependenciesResolver, null);
        return invocation.getCachedResult()
            .<TransformationResult>map(PrecomputedTransformationResult::new)
            .orElseGet(() -> {
                TransformationOperation operation = new TransformationOperation(invocation, "Transform " + initialSubject.getDisplayName() + " with " + transformation.getDisplayName());
                actions.add(operation);
                return operation;
            });
    }
}
