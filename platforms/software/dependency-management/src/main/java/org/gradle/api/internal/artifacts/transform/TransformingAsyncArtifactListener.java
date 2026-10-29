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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BrokenArtifacts;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Try;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.lazy.Lazy;
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
            public void visitArtifact(DisplayName artifactSetName, VariantIdentifier sourceVariantId, ImmutableAttributes attributes, ImmutableCapabilities variantCapabilities, ResolvableArtifact artifact) {
                TransformedArtifact transformedArtifact = new TransformedArtifact(artifactSetName, sourceVariantId, target, capabilities, artifact, transformSteps);
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
        private final DisplayName artifactSetName;
        private final ImmutableCapabilities capabilities;
        private final VariantIdentifier sourceVariantId;
        private final ResolvableArtifact artifact;
        private final ImmutableAttributes target;
        private final List<BoundTransformStep> transformSteps;
        private @Nullable Try<ImmutableList<File>> transformedFiles;
        private @Nullable Lazy<Try<TransformStepSubject>> invocation;

        public TransformedArtifact(
            DisplayName artifactSetName,
            VariantIdentifier sourceVariantId,
            ImmutableAttributes target,
            ImmutableCapabilities capabilities,
            ResolvableArtifact artifact,
            List<BoundTransformStep> transformSteps
        ) {
            this.artifactSetName = artifactSetName;
            this.sourceVariantId = sourceVariantId;
            this.artifact = artifact;
            this.target = target;
            this.capabilities = capabilities;
            this.transformSteps = transformSteps;
        }

        public DisplayName getArtifactSetName() {
            return artifactSetName;
        }

        public VariantIdentifier getSourceVariantId() {
            return sourceVariantId;
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

        @VisibleForTesting
        synchronized boolean hasPendingInvocation() {
            return invocation != null;
        }

        /**
         * Returns true if this artifact should be queued for execution, false when a value is already available.
         */
        private boolean prepareInvocation() {
            synchronized (this) {
                if (transformedFiles != null) {
                    // Already have a result, no need to execute
                    return false;
                }
            }
            if (!artifact.getFileSource().isFinalized()) {
                // No input artifact yet, should execute
                return true;
            }
            if (resultWithoutTransform() != null) {
                // The input artifact failed or does not exist, no need to execute
                return false;
            }

            Lazy<Try<TransformStepSubject>> invocation = createInvocation();
            synchronized (this) {
                if (transformedFiles != null) {
                    // Another thread stored a result while we were creating the invocation, so drop ours,
                    // as nothing would ever release it if we kept it
                    return false;
                }
                if (invocation.isInitialized()) {
                    // Have already executed the transform, no need to execute
                    storeResult(invocation.get().map(TransformStepSubject::getFiles));
                    return false;
                } else {
                    // Have not executed the transform, should execute
                    this.invocation = invocation;
                    return true;
                }
            }
        }

        private Try<ImmutableList<File>> finalizeValue() {
            synchronized (this) {
                Try<ImmutableList<File>> currentFiles = transformedFiles;
                if (currentFiles != null) {
                    return currentFiles;
                }
            }

            artifact.getFileSource().finalizeIfNotAlready();
            Try<ImmutableList<File>> resultWithoutTransform = resultWithoutTransform();
            if (resultWithoutTransform != null) {
                return resultWithoutTransform;
            }

            Lazy<Try<TransformStepSubject>> invocation;
            synchronized (this) {
                Try<ImmutableList<File>> currentFiles = transformedFiles;
                if (currentFiles != null) {
                    // Another thread stored a result since the first check and released the invocation, so use that result
                    // instead of creating a new invocation and running the transform again
                    return currentFiles;
                }
                invocation = this.invocation;
            }

            if (invocation == null) {
                invocation = createInvocation();
            }
            return storeResult(invocation.get().map(TransformStepSubject::getFiles));
        }

        /**
         * Stores and returns the result for an input artifact that doesn't need transforming. That is the case when it
         * failed to resolve, or when it is an optional input artifact that does not exist, which results in no output files.
         * The input artifact must already be finalized.
         *
         * @return the stored result if the input artifact doesn't need transforming, or null otherwise.
         */
        private @Nullable Try<ImmutableList<File>> resultWithoutTransform() {
            Try<@Nullable File> input = artifact.getFileSource().getValue();
            if (!input.isSuccessful()) {
                // Failed to resolve input artifact
                return storeResult(Try.failure(input.getFailure().get()));
            }
            if (input.get() == null) {
                // Optional input artifact does not exist, nothing to transform
                return storeResult(Try.successful(ImmutableList.of()));
            }
            return null;
        }

        private synchronized Try<ImmutableList<File>> storeResult(Try<ImmutableList<File>> result) {
            // The first stored result wins, so every caller sees the same result
            Try<ImmutableList<File>> currentFiles = transformedFiles;
            if (currentFiles == null) {
                currentFiles = result;
                transformedFiles = result;
            }
            // We only need the files from here on, so the invocation is cleared,
            // since it can keep the whole transform execution alive for as long as this artifact is cached
            invocation = null;
            return currentFiles;
        }

        private Lazy<Try<TransformStepSubject>> createInvocation() {
            TransformStepSubject initialSubject = TransformStepSubject.initial(artifact);
            BoundTransformStep initialStep = transformSteps.get(0);
            Lazy<Try<TransformStepSubject>> invocation = initialStep.getTransformStep()
                .createInvocation(initialSubject, initialStep.getUpstreamDependencies(), null);
            for (int i = 1; i < transformSteps.size(); i++) {
                BoundTransformStep nextStep = transformSteps.get(i);
                invocation = invocation
                    .flatMap(intermediateResult -> intermediateResult
                        .map(intermediateSubject -> nextStep.getTransformStep()
                            .createInvocation(intermediateSubject, nextStep.getUpstreamDependencies(), null))
                        .getOrMapFailure(failure -> Lazy.fixed(Try.failure(failure))));
            }
            return invocation;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            Try<ImmutableList<File>> transformedFiles = finalizeValue();
            transformedFiles.ifSuccessfulOrElse(
                files -> {
                    for (File output : files) {
                        ResolvableArtifact resolvedArtifact = artifact.transformedTo(output);
                        visitor.visitArtifact(artifactSetName, sourceVariantId, target, capabilities, resolvedArtifact);
                    }
                },
                failure -> visitor.visitFailure(
                    new TransformException(String.format("Failed to transform %s to match attributes %s.", artifact.getId().getDisplayName(), target), failure))
            );
        }
    }
}
