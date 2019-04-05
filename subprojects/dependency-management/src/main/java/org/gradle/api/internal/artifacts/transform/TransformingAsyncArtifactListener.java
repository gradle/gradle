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
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Map;
import java.util.Optional;

class TransformingAsyncArtifactListener implements ResolvedArtifactSet.AsyncArtifactListener {
    private final Map<ComponentArtifactIdentifier, TransformationResult> artifactResults;
    private final Map<File, TransformationResult> fileResults;
    private final ExecutionGraphDependenciesResolver dependenciesResolver;
    private final TransformationNodeRegistry transformationNodeRegistry;
    private final BuildOperationQueue<RunnableBuildOperation> actions;
    private final ResolvedArtifactSet.AsyncArtifactListener delegate;
    private final Transformation transformation;

    TransformingAsyncArtifactListener(
        Transformation transformation,
        ResolvedArtifactSet.AsyncArtifactListener delegate,
        BuildOperationQueue<RunnableBuildOperation> actions,
        Map<ComponentArtifactIdentifier, TransformationResult> artifactResults,
        Map<File, TransformationResult> fileResults,
        ExecutionGraphDependenciesResolver dependenciesResolver,
        TransformationNodeRegistry transformationNodeRegistry
    ) {
        this.artifactResults = artifactResults;
        this.actions = actions;
        this.transformation = transformation;
        this.delegate = delegate;
        this.fileResults = fileResults;
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
            TransformationOperation operation = new TransformationOperation(transformation, initialSubject, dependenciesResolver);
            artifactResults.put(artifactId, operation);
            // If we are here, then the transform has not been scheduled.
            // So either
            //   1) the transformed variant is not declared as an input for a work item or resolved at configuration time, or
            //   2) the artifact to transform is an external artifact.
            // For 1), we don't do any performance optimizations since transformed variants should be declared as input to some work.
            // For 2), either the artifact has just been downloaded or it was already downloaded earlier.
            // If it has just been downloaded, then, since downloads happen in parallel, we are already on a worker thread and we use it to execute the transform.
            // If it has been downloaded earlier, then there is a high chance that the transformed artifact is already in a Gradle user home workspace and up-to-date.
            // Using the BuildOperationQueue here to only realize that the result of the transformation is up-to-date in the Gradle user home workspace has a performance impact,
            // so we are executing the up-to-date transform operation in place.
            operation.run(null);
        }
    }

    @Override
    public boolean requireArtifactFiles() {
        // Always need the files, as we need to run the transform in order to calculate the output artifacts.
        return true;
    }

    @Override
    public boolean includeFileDependencies() {
        return delegate.includeFileDependencies();
    }

    @Override
    public void fileAvailable(File file) {
        TransformationSubject initialSubject = TransformationSubject.initial(file);
        TransformationOperation operation = new TransformationOperation(transformation, initialSubject, dependenciesResolver);
        fileResults.put(file, operation);
        // We expect file transformations to be executed in an immediate way,
        // since they cannot be scheduled early.
        // To allow file transformations to run in parallel, we use the BuildOperationQueue.
        actions.add(operation);
    }
}
