/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.steps;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.InputExecutionState;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class CaptureStateBeforeExecutionStep<C extends WorkDeterminedContext, R extends CachingResult> extends BuildOperationStep<C, R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureStateBeforeExecutionStep.class);

    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final OutputSnapshotter outputSnapshotter;
    private final OverlappingOutputDetector overlappingOutputDetector;
    private final Step<? super BeforeExecutionContext, ? extends R> delegate;

    public CaptureStateBeforeExecutionStep(
        BuildOperationExecutor buildOperationExecutor,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        Step<? super BeforeExecutionContext, ? extends R> delegate
    ) {
        super(buildOperationExecutor);
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.outputSnapshotter = outputSnapshotter;
        this.overlappingOutputDetector = overlappingOutputDetector;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        Optional<BeforeExecutionState> beforeExecutionState = work.getWorkspaceProvider().getHistory()
            .map(history -> captureExecutionState(work, context));
        return delegate.execute(work, new BeforeExecutionContext(context, beforeExecutionState.orElse(null)));
    }

    @Nonnull
    private BeforeExecutionState captureExecutionState(UnitOfWork work, MutableWorkspaceContext context) {
        return operation(operationContext -> {
                ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots;
                unfilteredOutputSnapshots = outputSnapshotter.snapshotOutputs(work, context.getMutableWorkspaceLocation());

                OverlappingOutputs overlappingOutputs = detectOverlappingOutputs(work, context, unfilteredOutputSnapshots);

                BeforeExecutionState executionState = captureExecutionStateWithOutputs(work, context, unfilteredOutputSnapshots, overlappingOutputs);
                operationContext.setResult(Operation.Result.INSTANCE);
                return executionState;
            },
            BuildOperationDescriptor
                .displayName("Snapshot inputs and outputs before executing " + work.getDisplayName())
                .details(Operation.Details.INSTANCE)
        );
    }

    @Nullable
    private OverlappingOutputs detectOverlappingOutputs(UnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots) {
        if (work.getOverlappingOutputHandling() == UnitOfWork.OverlappingOutputHandling.IGNORE_OVERLAPS) {
            return null;
        }
        ImmutableSortedMap<String, FileSystemSnapshot> previousOutputSnapshots = context.getPreviousExecutionState()
            .map(PreviousExecutionState::getOutputFilesProducedByWork)
            .orElse(ImmutableSortedMap.of());
        return overlappingOutputDetector.detect(previousOutputSnapshots, unfilteredOutputSnapshots);
    }

    private BeforeExecutionState captureExecutionStateWithOutputs(UnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots, @Nullable OverlappingOutputs overlappingOutputs) {
        Optional<PreviousExecutionState> previousExecutionState = context.getPreviousExecutionState();

        ImplementationsBuilder implementationsBuilder = new ImplementationsBuilder(classLoaderHierarchyHasher);
        work.visitImplementations(implementationsBuilder);
        ImplementationSnapshot implementation = implementationsBuilder.getImplementation();
        ImmutableList<ImplementationSnapshot> additionalImplementations = implementationsBuilder.getAdditionalImplementations();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", work.getDisplayName(), implementation);
            LOGGER.debug("Additional implementations for {}: {}", work.getDisplayName(), additionalImplementations);
        }

        ImmutableSortedMap<String, ValueSnapshot> previousInputPropertySnapshots = previousExecutionState
            .map(InputExecutionState::getInputProperties)
            .orElse(ImmutableSortedMap.of());
        ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousInputFileFingerprints = previousExecutionState
            .map(InputExecutionState::getInputFileProperties)
            .orElse(ImmutableSortedMap.of());

        InputFingerprinter.Result newInputs = work.getInputFingerprinter().fingerprintInputProperties(
            previousInputPropertySnapshots,
            previousInputFileFingerprints,
            context.getInputProperties(),
            context.getInputFileProperties(),
            work::visitRegularInputs
        );

        return new DefaultBeforeExecutionState(
            implementation,
            additionalImplementations,
            newInputs.getAllValueSnapshots(),
            newInputs.getAllFileFingerprints(),
            unfilteredOutputSnapshots,
            overlappingOutputs
        );
    }

    private static class ImplementationsBuilder implements UnitOfWork.ImplementationVisitor {
        private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
        private ImplementationSnapshot implementation;
        private final ImmutableList.Builder<ImplementationSnapshot> additionalImplementations = ImmutableList.builder();

        public ImplementationsBuilder(ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
            this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        }

        @Override
        public void visitImplementation(Class<?> implementation) {
            visitImplementation(ImplementationSnapshot.of(implementation, classLoaderHierarchyHasher));
        }

        @Override
        public void visitImplementation(ImplementationSnapshot implementation) {
            if (this.implementation == null) {
                this.implementation = implementation;
            } else {
                this.additionalImplementations.add(implementation);
            }
        }

        public ImplementationSnapshot getImplementation() {
            if (implementation == null) {
                throw new IllegalStateException("No implementation is set");
            }
            return implementation;
        }

        public ImmutableList<ImplementationSnapshot> getAdditionalImplementations() {
            return additionalImplementations.build();
        }
    }

    /*
     * This operation is only used here temporarily. Should be replaced with a more stable operation in the long term.
     */
    public interface Operation extends BuildOperationType<Operation.Details, Operation.Result> {
        interface Details {
            Details INSTANCE = new Details() {
            };
        }

        interface Result {
            Result INSTANCE = new Result() {
            };
        }
    }
}
