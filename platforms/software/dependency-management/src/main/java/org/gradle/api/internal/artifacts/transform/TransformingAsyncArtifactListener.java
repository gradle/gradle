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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenArtifacts;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.Deferrable;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Try;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;

public class TransformingAsyncArtifactListener implements ResolvedArtifactSet.Visitor {
    private final List<BoundTransformStep> transformSteps;
    private final ImmutableAttributes target;
    private final ImmutableCapabilities capabilities;
    private final ImmutableList.Builder<ResolvedArtifactSet.Artifacts> result;

    public TransformingAsyncArtifactListener(
        List<BoundTransformStep> transformSteps,
        ImmutableAttributes target,
        ImmutableCapabilities capabilities,
        ImmutableList.Builder<ResolvedArtifactSet.Artifacts> result
    ) {
        this.transformSteps = transformSteps;
        this.target = target;
        this.capabilities = capabilities;
        this.result = result;
    }

    @Override
    public void visitArtifacts(ResolvedArtifactSet.Artifacts artifacts) {
        artifacts.visit(new ArtifactVisitor() {
            @Override
            public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ImmutableCapabilities variantCapabilities, ResolvableArtifact artifact) {
                TransformedArtifact transformedArtifact = new TransformedArtifact(variantName, target, capabilities, artifact, transformSteps);
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
        private final ImmutableCapabilities capabilities;
        private final ResolvableArtifact artifact;
        private final ImmutableAttributes target;
        private final List<BoundTransformStep> transformSteps;
        private Try<TransformStepSubject> transformedSubject;
        private Deferrable<Try<TransformStepSubject>> invocation;

        public TransformedArtifact(DisplayName variantName, ImmutableAttributes target, ImmutableCapabilities capabilities, ResolvableArtifact artifact, List<BoundTransformStep> transformSteps) {
            this.variantName = variantName;
            this.artifact = artifact;
            this.target = target;
            this.capabilities = capabilities;
            this.transformSteps = transformSteps;
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

        public ImmutableCapabilities getCapabilities() {
            return capabilities;
        }

        public List<BoundTransformStep> getTransformSteps() {
            return transformSteps;
        }

        @Override
        public void prepareForVisitingIfNotAlready() {
            // The parameters of the transforms should already be isolated prior to visiting this set.
            // However, in certain cases, the transform's parameters may not be isolated (eg https://github.com/gradle/gradle/issues/23116), so do this now
            // Those cases should be improved so that the parameters are always isolated, for example by always using work nodes to do this work
            for (BoundTransformStep step : transformSteps) {
                step.getTransformStep().isolateParametersIfNotAlready();
            }
        }

        @Override
        public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
            if (prepareInvocation()) {
                actions.add(this);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Execute transform chain: " + artifact.getId().getDisplayName());
        }

        @Override
        public void run(@Nullable BuildOperationContext context) {
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

            Deferrable<Try<TransformStepSubject>> invocation = createInvocation();
            synchronized (this) {
                this.invocation = invocation;
                if (invocation.getCompleted().isPresent()) {
                    // Have already executed the transform, no need to execute
                    transformedSubject = invocation.getCompleted().get();
                    return false;
                } else {
                    // Have not executed the transform, should execute
                    return true;
                }
            }
        }

        private Try<TransformStepSubject> finalizeValue() {
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

            Deferrable<Try<TransformStepSubject>> invocation;
            synchronized (this) {
                invocation = this.invocation;
            }

            if (invocation == null) {
                invocation = createInvocation();
            }
            Try<TransformStepSubject> result = invocation.completeAndGet();
            synchronized (this) {
                transformedSubject = result;
                return result;
            }
        }

        private Deferrable<Try<TransformStepSubject>> createInvocation() {
            TransformStepSubject initialSubject = TransformStepSubject.initial(artifact);
            BoundTransformStep initialStep = transformSteps.get(0);
            Deferrable<Try<TransformStepSubject>> invocation = initialStep.getTransformStep()
                .createInvocation(initialSubject, initialStep.getUpstreamDependencies(), null);
            for (int i = 1; i < transformSteps.size(); i++) {
                BoundTransformStep nextStep = transformSteps.get(i);
                invocation = invocation
                    .flatMap(intermediateResult -> intermediateResult
                        .map(intermediateSubject -> nextStep.getTransformStep()
                            .createInvocation(intermediateSubject, nextStep.getUpstreamDependencies(), null))
                        .getOrMapFailure(failure -> Deferrable.completed(Try.failure(failure))));
            }
            return invocation;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            Try<TransformStepSubject> transformedSubject = finalizeValue();
            transformedSubject.ifSuccessfulOrElse(
                subject -> {
                    for (File output : subject.getFiles()) {
                        ResolvableArtifact resolvedArtifact = artifact.transformedTo(output);
                        visitor.visitArtifact(variantName, target, capabilities, resolvedArtifact);
                    }
                },
                failure -> visitor.visitFailure(
                    new TransformException(String.format("Failed to transform %s to match attributes %s.", artifact.getId().getDisplayName(), target), failure))
            );
        }
    }
}
