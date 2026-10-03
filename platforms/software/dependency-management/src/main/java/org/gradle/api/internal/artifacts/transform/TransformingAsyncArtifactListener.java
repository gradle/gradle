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
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * A transform chain execution for a single artifact. Thread safe: the result is computed at most once,
     * regardless of how many threads call {@link #startFinalization}, {@link #run} or {@link #visit} concurrently.
     */
    public static class TransformedArtifact implements ResolvedArtifactSet.Artifacts, RunnableBuildOperation {
        private final DisplayName artifactSetName;
        private final ImmutableCapabilities capabilities;
        private final VariantIdentifier sourceVariantId;
        private final ResolvableArtifact artifact;
        private final ImmutableAttributes target;
        private final List<BoundTransformStep> transformSteps;

        // The terminal result, set at most once via setResult(). Volatile, so an available result is read without synchronization.
        private volatile @Nullable Try<ImmutableList<File>> transformedFiles;
        // The pending invocation, created at most once and released when the result is set. Guarded by this instance's monitor.
        private @Nullable Deferrable<Try<ImmutableList<File>>> deferredTransformedFiles;

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

        @VisibleForTesting
        boolean hasPendingInvocation() {
            synchronized (this) {
                return deferredTransformedFiles != null;
            }
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
            if (transformedFiles != null) {
                // Already have a result, no need to execute
                return false;
            }
            if (!artifact.getFileSource().isFinalized()) {
                // No input artifact yet, should execute
                return true;
            }
            Optional<Throwable> inputFailure = artifact.getFileSource().getValue().getFailure();
            if (inputFailure.isPresent()) {
                // Failed to resolve the input artifact, no need to execute
                setResult(Try.failure(inputFailure.get()));
                return false;
            }

            Deferrable<Try<ImmutableList<File>>> invocation = invocationForExecution();
            if (invocation == null) {
                // A result became available concurrently, no need to execute
                return false;
            }
            Optional<Try<ImmutableList<File>>> completed = invocation.getCompleted();
            if (completed.isPresent()) {
                // Have already executed the transform, no need to execute
                setResult(completed.get());
                return false;
            }
            // Have not executed the transform, should execute
            return true;
        }

        private Try<ImmutableList<File>> finalizeValue() {
            Try<ImmutableList<File>> result = transformedFiles;
            if (result != null) {
                return result;
            }

            artifact.getFileSource().finalizeIfNotAlready();
            Optional<Throwable> inputFailure = artifact.getFileSource().getValue().getFailure();
            if (inputFailure.isPresent()) {
                // Failed to resolve the input artifact
                return setResult(Try.failure(inputFailure.get()));
            }

            Deferrable<Try<ImmutableList<File>>> invocation = invocationForExecution();
            if (invocation != null) {
                return setResult(invocation.completeAndGet());
            }
            // A concurrent invocation has already set the result
            return Objects.requireNonNull(transformedFiles);
        }

        /**
         * Returns the invocation of the transform chain, creating it on first use, or null when the result is already available.
         * The invocation is created at most once, since its creation is expensive and has side effects. Creation deliberately
         * runs under the monitor; this is safe because it never calls back into this instance.
         */
        private @Nullable Deferrable<Try<ImmutableList<File>>> invocationForExecution() {
            synchronized (this) {
                if (transformedFiles != null) {
                    return null;
                }
                Deferrable<Try<ImmutableList<File>>> currentDeferred = deferredTransformedFiles;
                if (currentDeferred == null) {
                    currentDeferred = createDeferredTransformedFiles();
                    deferredTransformedFiles = currentDeferred;
                }
                return currentDeferred;
            }
        }

        /**
         * Sets the terminal result unless already set and releases the invocation machinery.
         */
        private Try<ImmutableList<File>> setResult(Try<ImmutableList<File>> result) {
            synchronized (this) {
                Try<ImmutableList<File>> currentTransformedFiles = transformedFiles;
                if (currentTransformedFiles == null) {
                    currentTransformedFiles = result;
                    transformedFiles = result;
                }
                deferredTransformedFiles = null;
                return currentTransformedFiles;
            }
        }

        private Deferrable<Try<ImmutableList<File>>> createDeferredTransformedFiles() {
            TransformStepSubject initialSubject = TransformStepSubject.initial(artifact);
            BoundTransformStep initialStep = transformSteps.get(0);
            Deferrable<Try<TransformStepSubject>> invocation = initialStep.getTransformStep()
                .createInvocation(initialSubject, initialStep.getUpstreamDependencies(), null);
            for (int i = 1; i < transformSteps.size(); i++) {
                BoundTransformStep nextStep = transformSteps.get(i);
                invocation = invocation.flatMap(previousResult -> {
                    if (previousResult.isSuccessful()) {
                        // The subject of a successful invocation is never null
                        TransformStepSubject previousSubject = Objects.requireNonNull(previousResult.get());
                        return nextStep.getTransformStep().createInvocation(previousSubject, nextStep.getUpstreamDependencies(), null);
                    }
                    // Propagate the failure
                    return Deferrable.completed(previousResult);
                });
            }
            // Keep only the files, so that the subject chain can be collected once the invocation completes
            return invocation.map(result -> result.map(TransformStepSubject::getFiles));
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            Try<ImmutableList<File>> result = finalizeValue();
            result.ifSuccessfulOrElse(
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
