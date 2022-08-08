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

import com.google.common.collect.ImmutableList;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenArtifacts;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Try;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public class TransformingAsyncArtifactListener implements ResolvedArtifactSet.Visitor {
    private final List<BoundTransformationStep> transformationSteps;
    private final ImmutableAttributes target;
    private final List<? extends Capability> capabilities;
    private final ImmutableList.Builder<ResolvedArtifactSet.Artifacts> result;

    public TransformingAsyncArtifactListener(
        List<BoundTransformationStep> transformationSteps,
        ImmutableAttributes target,
        List<? extends Capability> capabilities,
        ImmutableList.Builder<ResolvedArtifactSet.Artifacts> result
    ) {
        this.transformationSteps = transformationSteps;
        this.target = target;
        this.capabilities = capabilities;
        this.result = result;
    }

    @Override
    public void visitArtifacts(ResolvedArtifactSet.Artifacts artifacts) {
        artifacts.visit(new ArtifactVisitor() {
            @Override
            public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, List<? extends Capability> variantCapabilities, ResolvableArtifact artifact) {
                TransformedArtifact transformedArtifact = new TransformedArtifact(variantName, target, capabilities, artifact, transformationSteps);
                result.add(transformedArtifact);
            }

            @Override
            public boolean requireArtifactFiles() {
                return false;
            }

            @Override
            public void visitFailure(Throwable failure) {
                result.add(new BrokenArtifacts(failure));
            }
        });
    }

    @Override
    public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        // Visit everything
        return FileCollectionStructureVisitor.VisitType.Visit;
    }

    public static class TransformedArtifact implements ResolvedArtifactSet.Artifacts, RunnableBuildOperation {
        private final DisplayName variantName;
        private final List<? extends Capability> capabilities;
        private final ResolvableArtifact artifact;
        private final ImmutableAttributes target;
        private final List<BoundTransformationStep> transformationSteps;
        private Try<TransformationSubject> transformedSubject;
        private CacheableInvocation<TransformationSubject> invocation;

        public TransformedArtifact(DisplayName variantName, ImmutableAttributes target, List<? extends Capability> capabilities, ResolvableArtifact artifact, List<BoundTransformationStep> transformationSteps) {
            this.variantName = variantName;
            this.artifact = artifact;
            this.target = target;
            this.capabilities = capabilities;
            this.transformationSteps = transformationSteps;
        }

        public DisplayName getVariantName() {
            return variantName;
        }

        public ResolvableArtifact getArtifact() {
            return artifact;
        }

        public ImmutableAttributes getTarget() {
            return target;
        }

        public List<? extends Capability> getCapabilities() {
            return capabilities;
        }

        public List<BoundTransformationStep> getTransformationSteps() {
            return transformationSteps;
        }

        @Override
        public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
            if (prepareInvocation()) {
                actions.add(this);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Execute transform");
        }

        @Override
        public void run(@Nullable BuildOperationContext context) {
            finalizeValue();
        }

        @Override
        public void finalizeNow(boolean requireFiles) {
            finalizeValue();
        }

        /**
         * Returns true if this artifact should be queued for execution, false when a value is already available.
         */
        private boolean prepareInvocation() {
            synchronized (this) {
                if (transformedSubject != null) {
                    // Already have a result, no need to execute
                    return false;
                }
            }
            if (!artifact.getFileSource().isFinalized()) {
                // No input artifact yet, should execute
                return true;
            }
            if (!artifact.getFileSource().getValue().isSuccessful()) {
                synchronized (this) {
                    // Failed to resolve input artifact, no need to execute
                    transformedSubject = Try.failure(artifact.getFileSource().getValue().getFailure().get());
                    return false;
                }
            }

            CacheableInvocation<TransformationSubject> invocation = createInvocation();
            synchronized (this) {
                this.invocation = invocation;
                if (invocation.getCachedResult().isPresent()) {
                    // Have already executed the transform, no need to execute
                    transformedSubject = invocation.getCachedResult().get();
                    return false;
                } else {
                    // Have not executed the transform, should execute
                    return true;
                }
            }
        }

        private Try<TransformationSubject> finalizeValue() {
            synchronized (this) {
                if (transformedSubject != null) {
                    return transformedSubject;
                }
            }

            artifact.getFileSource().finalizeIfNotAlready();
            if (!artifact.getFileSource().getValue().isSuccessful()) {
                synchronized (this) {
                    transformedSubject = Try.failure(artifact.getFileSource().getValue().getFailure().get());
                    return transformedSubject;
                }
            }

            CacheableInvocation<TransformationSubject> invocation;
            synchronized (this) {
                invocation = this.invocation;
            }

            if (invocation == null) {
                invocation = createInvocation();
            }
            Try<TransformationSubject> result = invocation.invoke();
            synchronized (this) {
                transformedSubject = result;
                return result;
            }
        }

        private CacheableInvocation<TransformationSubject> createInvocation() {
            TransformationSubject initialSubject = TransformationSubject.initial(artifact);
            BoundTransformationStep initialStep = transformationSteps.get(0);
            CacheableInvocation<TransformationSubject> invocation = initialStep.getTransformation().createInvocation(initialSubject, initialStep.getUpstreamDependencies(), null);
            for (int i = 1; i < transformationSteps.size(); i++) {
                BoundTransformationStep nextStep = transformationSteps.get(i);
                invocation = invocation.flatMap(intermediate -> nextStep.getTransformation().createInvocation(intermediate, nextStep.getUpstreamDependencies(), null));
            }
            return invocation;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            Try<TransformationSubject> transformedSubject = finalizeValue();
            transformedSubject.ifSuccessfulOrElse(
                subject -> {
                    for (File output : subject.getFiles()) {
                        ResolvableArtifact resolvedArtifact = artifact.transformedTo(output);
                        visitor.visitArtifact(variantName, target, capabilities, resolvedArtifact);
                    }
                },
                failure -> visitor.visitFailure(
                    new TransformException(String.format("Failed to transform %s to match attributes %s.", artifact.getId(), target), failure))
            );
        }
    }
}
