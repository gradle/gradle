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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.Action;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.Collection;

/**
 * A ResolvedArtifactSet wrapper that prepares artifacts in parallel when visiting the delegate.
 * This is done by collecting all artifacts to prepare and/or visit in a first step.
 * The collected artifacts are prepared in parallel and subsequently visited in sequence.
 */
public class ParallelResolveArtifactSet implements ResolvedArtifactSet {
    private final ResolvedArtifactSet delegate;
    private final BuildOperationProcessor buildOperationProcessor;

    public ParallelResolveArtifactSet(ResolvedArtifactSet delegate, BuildOperationProcessor buildOperationProcessor) {
        this.delegate = delegate;
        this.buildOperationProcessor = buildOperationProcessor;
    }

    @Override
    public void addPrepareActions(BuildOperationQueue<RunnableBuildOperation> actions, ArtifactVisitor visitor) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void collectBuildDependencies(Collection<? super TaskDependency> dest) {
        delegate.collectBuildDependencies(dest);
    }

    @Override
    public void visit(final ArtifactVisitor visitor) {
        // Create a snapshot so that we use the same set of backing variants for prepare and visit
        final ResolvedArtifactSet snapshot = delegate instanceof DynamicResolvedArtifactSet ? ((DynamicResolvedArtifactSet) delegate).snapshot() : delegate;

        // Execute all 'prepare' calls in parallel
        buildOperationProcessor.run(new Action<BuildOperationQueue<RunnableBuildOperation>>() {
            @Override
            public void execute(BuildOperationQueue<RunnableBuildOperation> buildOperationQueue) {
                snapshot.addPrepareActions(buildOperationQueue, visitor);
            }
        });

        // Now visit the set in order
        snapshot.visit(visitor);
    }
}
