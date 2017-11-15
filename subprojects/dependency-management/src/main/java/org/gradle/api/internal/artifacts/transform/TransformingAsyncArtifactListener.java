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

import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Map;

class TransformingAsyncArtifactListener implements ResolvedArtifactSet.AsyncArtifactListener {
    private final Map<ResolvableArtifact, TransformArtifactOperation> artifactResults;
    private final Map<File, TransformFileOperation> fileResults;
    private final BuildOperationQueue<RunnableBuildOperation> actions;
    private final ResolvedArtifactSet.AsyncArtifactListener delegate;
    private final ArtifactTransformer transform;

    TransformingAsyncArtifactListener(ArtifactTransformer transform, ResolvedArtifactSet.AsyncArtifactListener delegate, BuildOperationQueue<RunnableBuildOperation> actions, Map<ResolvableArtifact, TransformArtifactOperation> artifactResults, Map<File, TransformFileOperation> fileResults) {
        this.artifactResults = artifactResults;
        this.actions = actions;
        this.transform = transform;
        this.delegate = delegate;
        this.fileResults = fileResults;
    }

    @Override
    public void artifactAvailable(ResolvableArtifact artifact) {
        TransformArtifactOperation operation = new TransformArtifactOperation(artifact, transform);
        artifactResults.put(artifact, operation);
        if (transform.hasCachedResult(artifact.getFile())) {
            operation.run(null);
        } else {
            actions.add(operation);
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
        TransformFileOperation operation = new TransformFileOperation(file, transform);
        fileResults.put(file, operation);
        if (transform.hasCachedResult(file)) {
            operation.run(null);
        } else {
            actions.add(operation);
        }
    }
}
