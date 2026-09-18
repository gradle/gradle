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

import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.operations.BuildOperationExecutor;

import java.util.ArrayList;
import java.util.List;

/**
 * Traverses a {@link ResolvedArtifactSet} and visits the result.
 */
public final class ParallelResolveArtifactSet {

    private ParallelResolveArtifactSet() {
        // Private to prevent instantiation.
    }

    /**
     * Finalizes the given artifact set in parallel, then visits the artifacts serially.
     */
    public static void visitInParallel(
        ResolvedArtifactSet artifacts,
        BuildOperationExecutor buildOperationExecutor,
        ArtifactVisitor visitor
    ) {
        if (artifacts == ResolvedArtifactSet.EMPTY) {
            return;
        }

        List<ResolvedArtifactSet.Artifacts> results = new ArrayList<>();
        buildOperationExecutor.runAll(queue ->
            artifacts.visit(new ResolvedArtifactSet.Visitor() {
                @Override
                public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
                    return visitor.prepareForVisit(source);
                }

                @Override
                public void visitArtifacts(ResolvedArtifactSet.Artifacts artifacts) {
                    // TODO: Downloads here should use `BuildOperationQueue#addUnconstrained`, so that we can fetch
                    // more artifacts in parallel than there are worker leases. This is blocked on classifying the
                    // work submitted by `Artifact#startFinalization`: artifact transforms in this set that have not
                    // yet executed run here on-demand, and being CPU-bound they must stay lease-constrained.
                    artifacts.startFinalization(queue, visitor.requireArtifactFiles());
                    results.add(artifacts);
                }
            })
        );

        for (ResolvedArtifactSet.Artifacts result : results) {
            result.visit(visitor);
        }
    }

}
