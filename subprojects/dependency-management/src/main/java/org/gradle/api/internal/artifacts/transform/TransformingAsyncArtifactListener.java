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
import java.util.List;
import java.util.Map;

public class TransformingAsyncArtifactListener implements ResolvedArtifactSet.AsyncArtifactListener {
    private final List<BoundTransformationStep> transformationSteps;
    private final Map<ComponentArtifactIdentifier, TransformationResult> artifactResults;
    private final BuildOperationQueue<RunnableBuildOperation> actions;

    public TransformingAsyncArtifactListener(
        List<BoundTransformationStep> transformationSteps,
        BuildOperationQueue<RunnableBuildOperation> actions,
        Map<ComponentArtifactIdentifier, TransformationResult> artifactResults) {
        this.transformationSteps = transformationSteps;
        this.artifactResults = artifactResults;
        this.actions = actions;
    }

    @Override
    public void artifactAvailable(ResolvableArtifact artifact) {
        ComponentArtifactIdentifier artifactId = artifact.getId();
        File file = artifact.getFile();
        TransformationSubject initialSubject = TransformationSubject.initial(artifactId, file);
        TransformationResult result = createTransformationResult(initialSubject);
        artifactResults.put(artifactId, result);
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
        CacheableInvocation<TransformationSubject> invocation = createInvocation(initialSubject);
        return invocation.getCachedResult()
            .<TransformationResult>map(PrecomputedTransformationResult::new)
            .orElseGet(() -> {
                TransformationOperation operation = new TransformationOperation(invocation);
                actions.add(operation);
                return operation;
            });
    }

    private CacheableInvocation<TransformationSubject> createInvocation(TransformationSubject initialSubject) {
        BoundTransformationStep initialStep = transformationSteps.get(0);
        CacheableInvocation<TransformationSubject> invocation = initialStep.getTransformation().createInvocation(initialSubject, initialStep.getUpstreamDependencies(), null);
        for (int i = 1; i < transformationSteps.size(); i++) {
            BoundTransformationStep nextStep = transformationSteps.get(i);
            invocation = invocation.flatMap(intermediate -> nextStep.getTransformation().createInvocation(intermediate, nextStep.getUpstreamDependencies(), null));
        }
        return invocation;
    }
}
