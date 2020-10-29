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

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenArtifacts;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TransformingAsyncArtifactListener implements ResolvedArtifactSet.Visitor {
    private final List<BoundTransformationStep> transformationSteps;
    private final ResolvedArtifactSet.Visitor visitor;
    private final AttributeContainerInternal target;

    public TransformingAsyncArtifactListener(
        List<BoundTransformationStep> transformationSteps,
        AttributeContainerInternal target,
        ResolvedArtifactSet.Visitor visitor) {
        this.transformationSteps = transformationSteps;
        this.target = target;
        this.visitor = visitor;
    }

    @Override
    public void visitArtifacts(ResolvedArtifactSet.Artifacts artifacts) {
        artifacts.visit(new ArtifactVisitor() {
            @Override
            public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ResolvableArtifact artifact) {
                artifact.getFileSource().finalizeIfNotAlready();
                if (!artifact.getFileSource().getValue().isSuccessful()) {
                    visitor.visitArtifacts(new BrokenArtifacts(artifact.getFileSource().getValue().getFailure().get()));
                    return;
                }
                TransformedArtifact transformedArtifact = new TransformedArtifact(variantName, target, artifact);
                visitor.visitArtifacts(transformedArtifact);
            }

            @Override
            public boolean requireArtifactFiles() {
                return false;
            }

            @Override
            public void visitFailure(Throwable failure) {
                visitor.visitArtifacts(new BrokenArtifacts(failure));
            }
        });
    }

    @Override
    public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        // Visit everything
        return FileCollectionStructureVisitor.VisitType.Visit;
    }

    private final class TransformedArtifact implements ResolvedArtifactSet.Artifacts {
        private final DisplayName variantName;
        private final ResolvableArtifact artifact;
        private final AttributeContainerInternal target;
        private final TransformationResult result;
        private final List<TransformationOperation> pending = new ArrayList<>();

        public TransformedArtifact(DisplayName variantName, AttributeContainerInternal target, ResolvableArtifact artifact) {
            this.variantName = variantName;
            this.artifact = artifact;
            this.target = target;
            TransformationSubject initialSubject = TransformationSubject.initial(artifact);
            CacheableInvocation<TransformationSubject> invocation = createInvocation(initialSubject);
            result = invocation.getCachedResult()
                .<TransformationResult>map(PrecomputedTransformationResult::new)
                .orElseGet(() -> {
                    TransformationOperation operation = new TransformationOperation(invocation);
                    pending.add(operation);
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

        @Override
        public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
            for (TransformationOperation operation : pending) {
                actions.add(operation);
            }
        }

        @Override
        public void finalizeNow(boolean requireFiles) {
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            result.getTransformedSubject().ifSuccessfulOrElse(
                transformedSubject -> {
                    for (File output : transformedSubject.getFiles()) {
                        ResolvableArtifact resolvedArtifact = artifact.transformedTo(output);
                        visitor.visitArtifact(variantName, target, resolvedArtifact);
                    }
                },
                failure -> visitor.visitFailure(
                    new TransformException(String.format("Failed to transform %s to match attributes %s.", artifact.getId(), target), failure))
            );
        }
    }
}
