/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.file.FileCollection;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionResult;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.changes.InputChangesInternal;
import org.gradle.internal.execution.history.impl.DefaultAfterExecutionState;
import org.gradle.internal.file.TreeType;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

import java.io.File;
import java.time.Duration;
import java.util.Optional;

import static org.gradle.internal.execution.history.impl.OutputSnapshotUtil.filterOutputsAfterExecution;

/**
 * Capture the state of the unit of work after its execution finished.
 *
 * All changes to the outputs must be done at this point, so this step needs to be around anything
 * which uses an {@link ChangingOutputsContext}.
 */
public class CaptureStateAfterExecutionStep<C extends InputChangesContext> extends BuildOperationStep<C, AfterExecutionResult> {
    private final UniqueId buildInvocationScopeId;
    private final OutputSnapshotter outputSnapshotter;
    private final OutputChangeListener outputChangeListener;
    private final Step<? super ChangingOutputsContext, ? extends Result> delegate;

    public CaptureStateAfterExecutionStep(
        BuildOperationExecutor buildOperationExecutor,
        UniqueId buildInvocationScopeId,
        OutputSnapshotter outputSnapshotter,
        OutputChangeListener outputChangeListener,
        Step<? super ChangingOutputsContext, ? extends Result> delegate
    ) {
        super(buildOperationExecutor);
        this.buildInvocationScopeId = buildInvocationScopeId;
        this.outputSnapshotter = outputSnapshotter;
        this.outputChangeListener = outputChangeListener;
        this.delegate = delegate;
    }

    @Override
    public AfterExecutionResult execute(UnitOfWork work, C context) {
        Result result = executeDelegateBroadcastingChanges(work, context);
        Duration duration = result.getDuration();
        Optional<AfterExecutionState> afterExecutionState = context.getBeforeExecutionState()
            .flatMap(beforeExecutionState -> captureStateAfterExecution(work, context, beforeExecutionState, duration));

        return new AfterExecutionResult() {
            @Override
            public Optional<AfterExecutionState> getAfterExecutionState() {
                return afterExecutionState;
            }

            @Override
            public Try<ExecutionResult> getExecutionResult() {
                return result.getExecutionResult();
            }

            @Override
            public Duration getDuration() {
                return duration;
            }
        };
    }

    private Result executeDelegateBroadcastingChanges(UnitOfWork work, C context) {
        ImmutableList.Builder<String> builder = ImmutableList.builder();
        work.visitOutputs(context.getWorkspace(), new UnitOfWork.OutputVisitor() {
            @Override
            public void visitOutputProperty(String propertyName, TreeType type, File root, FileCollection contents) {
                builder.add(root.getAbsolutePath());
            }

            @Override
            public void visitLocalState(File localStateRoot) {
                builder.add(localStateRoot.getAbsolutePath());
            }

            @Override
            public void visitDestroyable(File destroyableRoot) {
                builder.add(destroyableRoot.getAbsolutePath());
            }
        });
        ImmutableList<String> outputsToBeChanged = builder.build();
        outputChangeListener.invalidateCachesFor(outputsToBeChanged);
        try {
            return delegate.execute(work, wrapInChangesOutputsContext(context));
        } finally {
            outputChangeListener.invalidateCachesFor(outputsToBeChanged);
        }
    }

    private Optional<AfterExecutionState> captureStateAfterExecution(UnitOfWork work, BeforeExecutionContext context, BeforeExecutionState beforeExecutionState, Duration duration) {
        return operation(
            operationContext -> {
                try {
                    Timer timer = Time.startTimer();
                    ImmutableSortedMap<String, FileSystemSnapshot> outputsProducedByWork = captureOutputs(work, context, beforeExecutionState);
                    long snapshotOutputDuration = timer.getElapsedMillis();

                    // The origin execution time is recorded as “work duration” + “output snapshotting duration”,
                    // As this is _roughly_ the amount of time that is avoided by reusing the outputs,
                    // which is currently the _only_ thing this value is used for.
                    Duration originExecutionTime = duration.plus(Duration.ofMillis(snapshotOutputDuration));
                    OriginMetadata originMetadata = new OriginMetadata(buildInvocationScopeId.asString(), originExecutionTime);
                    AfterExecutionState afterExecutionState = new DefaultAfterExecutionState(beforeExecutionState, outputsProducedByWork, originMetadata, false);
                    operationContext.setResult(Operation.Result.INSTANCE);
                    return Optional.of(afterExecutionState);
                } catch (OutputSnapshotter.OutputFileSnapshottingException e) {
                    work.handleUnreadableOutputs(e);
                    operationContext.setResult(Operation.Result.INSTANCE);
                    return Optional.empty();
                }
            },
            BuildOperationDescriptor
                .displayName("Snapshot outputs after executing " + work.getDisplayName())
                .details(Operation.Details.INSTANCE)
        );
    }

    private ImmutableSortedMap<String, FileSystemSnapshot> captureOutputs(UnitOfWork work, BeforeExecutionContext context, BeforeExecutionState beforeExecutionState) {
        ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshotsAfterExecution = outputSnapshotter.snapshotOutputs(work, context.getWorkspace());

        if (beforeExecutionState.getDetectedOverlappingOutputs().isPresent()) {
            ImmutableSortedMap<String, FileSystemSnapshot> previousExecutionOutputSnapshots = context.getPreviousExecutionState()
                .map(PreviousExecutionState::getOutputFilesProducedByWork)
                .orElse(ImmutableSortedMap.of());

            ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshotsBeforeExecution = context.getBeforeExecutionState()
                .map(BeforeExecutionState::getOutputFileLocationSnapshots)
                .orElse(ImmutableSortedMap.of());

            return filterOutputsAfterExecution(previousExecutionOutputSnapshots, unfilteredOutputSnapshotsBeforeExecution, unfilteredOutputSnapshotsAfterExecution);
        } else {
            return unfilteredOutputSnapshotsAfterExecution;
        }
    }

    private ChangingOutputsContext wrapInChangesOutputsContext(C context) {
        return new ChangingOutputsContext() {
            @Override
            public File getWorkspace() {
                return context.getWorkspace();
            }

            @Override
            public Optional<ExecutionHistoryStore> getHistory() {
                return context.getHistory();
            }

            @Override
            public Optional<ValidationResult> getValidationProblems() {
                return context.getValidationProblems();
            }

            @Override
            public Optional<PreviousExecutionState> getPreviousExecutionState() {
                return context.getPreviousExecutionState();
            }

            @Override
            public Optional<InputChangesInternal> getInputChanges() {
                return context.getInputChanges();
            }

            @Override
            public boolean isIncrementalExecution() {
                return context.isIncrementalExecution();
            }

            @Override
            public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
                return context.getInputProperties();
            }

            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                return context.getInputFileProperties();
            }

            @Override
            public UnitOfWork.Identity getIdentity() {
                return context.getIdentity();
            }

            @Override
            public Optional<String> getNonIncrementalReason() {
                return context.getNonIncrementalReason();
            }

            @Override
            public WorkValidationContext getValidationContext() {
                return context.getValidationContext();
            }

            @Override
            public Optional<BeforeExecutionState> getBeforeExecutionState() {
                return context.getBeforeExecutionState();
            }
        };
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
