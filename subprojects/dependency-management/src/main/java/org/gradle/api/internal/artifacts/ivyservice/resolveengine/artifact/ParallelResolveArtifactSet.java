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
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.operations.BuildOperationConstraint;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper that prepares artifacts in parallel when visiting the delegate.
 * This is done by collecting all artifacts to prepare and/or visit in a first step.
 * The collected artifacts are prepared in parallel and subsequently visited in sequence.
 */
public abstract class ParallelResolveArtifactSet {
    private static final EmptySet EMPTY = new EmptySet();

    public abstract void visit(ArtifactVisitor visitor);

    public static ParallelResolveArtifactSet wrap(ResolvedArtifactSet artifacts, BuildOperationExecutor buildOperationProcessor) {
        if (artifacts == ResolvedArtifactSet.EMPTY) {
            return EMPTY;
        }
        return new VisitingSet(artifacts, buildOperationProcessor);
    }

    private static class EmptySet extends ParallelResolveArtifactSet {
        @Override
        public void visit(ArtifactVisitor visitor) {
        }
    }

    private static class VisitingSet extends ParallelResolveArtifactSet {
        private final ResolvedArtifactSet artifacts;
        private final BuildOperationExecutor buildOperationProcessor;

        VisitingSet(ResolvedArtifactSet artifacts, BuildOperationExecutor buildOperationProcessor) {
            this.artifacts = artifacts;
            this.buildOperationProcessor = buildOperationProcessor;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            // Start preparing the result
            StartVisitAction visitAction = new StartVisitAction(visitor);
            buildOperationProcessor.runAll(visitAction, BuildOperationConstraint.UNCONSTRAINED);

            // Now visit the result in order
            visitAction.visitResults();
        }

        private class StartVisitAction implements Action<BuildOperationQueue<RunnableBuildOperation>>, ResolvedArtifactSet.Visitor {
            private final ArtifactVisitor visitor;
            private final List<ResolvedArtifactSet.Artifacts> results = new ArrayList<>();
            private BuildOperationQueue<RunnableBuildOperation> queue;

            StartVisitAction(ArtifactVisitor visitor) {
                this.visitor = visitor;
            }

            @Override
            public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
                return visitor.prepareForVisit(source);
            }

            @Override
            public void visitArtifacts(ResolvedArtifactSet.Artifacts artifacts) {
                artifacts.startFinalization(queue, visitor.requireArtifactFiles());
                results.add(artifacts);
            }

            @Override
            public void execute(BuildOperationQueue<RunnableBuildOperation> buildOperationQueue) {
                this.queue = buildOperationQueue;
                artifacts.visit(this);
            }

            public void visitResults() {
                for (ResolvedArtifactSet.Artifacts result : results) {
                    result.visit(visitor);
                }
            }
        }
    }
}
