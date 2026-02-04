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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.InputVisitor;
import org.gradle.internal.execution.MutableUnitOfWork;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionInputState;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

import static org.gradle.internal.execution.MutableUnitOfWork.OverlappingOutputHandling.IGNORE_OVERLAPS;

public class CaptureMutableStateBeforeExecutionStep<C extends PreviousExecutionContext, R extends CachingResult> extends BuildOperationStep<C, R> {
    private final OutputSnapshotter outputSnapshotter;
    private final OverlappingOutputDetector overlappingOutputDetector;
    private final Step<? super MutableBeforeExecutionContext, ? extends R> delegate;

    public CaptureMutableStateBeforeExecutionStep(
        BuildOperationRunner buildOperationRunner,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        Step<? super MutableBeforeExecutionContext, ? extends R> delegate
    ) {
        super(buildOperationRunner);
        this.outputSnapshotter = outputSnapshotter;
        this.overlappingOutputDetector = overlappingOutputDetector;
        this.delegate = delegate;
    }

    @Override
    public R execute(UnitOfWork work, C context) {
        // TODO Make steps generic over mutable and immutable work
        return executeMutable((MutableUnitOfWork) work, context);
    }

    private R executeMutable(MutableUnitOfWork work, C context) {
        BeforeExecutionState beforeExecutionState;
        OverlappingOutputs overlappingOutputs;
        boolean shouldCaptureBeforeExecutionState = work.getHistory().isPresent();
        if (shouldCaptureBeforeExecutionState) {
            beforeExecutionState = captureExecutionState(work, context);
            overlappingOutputs = detectOverlappingOutputs(work, context, beforeExecutionState.getOutputFileLocationSnapshots());
        } else {
            beforeExecutionState = null;
            overlappingOutputs = null;
            // We still need to visit the inputs to ensure that the dependencies are validated
            work.visitMutableInputs(new InputVisitor() {
                @Override
                public void visitInputFileProperty(String propertyName, InputBehavior behavior, InputFileValueSupplier value) {
                    ((FileCollectionInternal) value.getFiles()).visitStructure(work.getInputDependencyChecker(context.getValidationContext()));
                }
            });
        }
        return delegate.execute(work, new MutableBeforeExecutionContext(context, beforeExecutionState, overlappingOutputs));
    }

    private BeforeExecutionState captureExecutionState(UnitOfWork work, PreviousExecutionContext context) {
        // TODO Remove once IntelliJ stops complaining about possible NPE
        //noinspection DataFlowIssue
        return operation(operationContext -> {
                ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots = outputSnapshotter.snapshotOutputs(work, context.getWorkspace());
                BeforeExecutionState executionState = captureExecutionStateWithOutputs(work, context, unfilteredOutputSnapshots);
                operationContext.setResult(Operation.Result.INSTANCE);
                return executionState;
            },
            BuildOperationDescriptor
                .displayName("Snapshot inputs and outputs before executing " + work.getDisplayName())
                .details(Operation.Details.INSTANCE)
        );
    }

    @Nullable
    private OverlappingOutputs detectOverlappingOutputs(MutableUnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots) {
        if (work.getOverlappingOutputHandling() == IGNORE_OVERLAPS) {
            return null;
        }
        ImmutableSortedMap<String, FileSystemSnapshot> previousOutputSnapshots = context.getPreviousExecutionState()
            .map(PreviousExecutionState::getOutputFilesProducedByWork)
            .orElse(ImmutableSortedMap.of());
        return overlappingOutputDetector.detect(previousOutputSnapshots, unfilteredOutputSnapshots);
    }

    private static BeforeExecutionState captureExecutionStateWithOutputs(UnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots) {
        Optional<PreviousExecutionState> previousExecutionState = context.getPreviousExecutionState();
        ImmutableSortedMap<String, ValueSnapshot> previousInputPropertySnapshots = previousExecutionState
            .map(ExecutionInputState::getInputProperties)
            .orElse(ImmutableSortedMap.of());
        ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousInputFileFingerprints = previousExecutionState
            .map(ExecutionInputState::getInputFileProperties)
            .orElse(ImmutableSortedMap.of());

        InputFingerprinter.Result newInputs = work.getInputFingerprinter().fingerprintInputProperties(
            previousInputPropertySnapshots,
            previousInputFileFingerprints,
            context.getInputProperties(),
            context.getInputFileProperties(),
            work::visitMutableInputs,
            work.getInputDependencyChecker(context.getValidationContext())
        );

        return new DefaultBeforeExecutionState(
            context.getImplementation(),
            context.getAdditionalImplementations(),
            newInputs.getAllValueSnapshots(),
            newInputs.getAllFileFingerprints(),
            unfilteredOutputSnapshots
        );
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
