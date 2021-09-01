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
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.OutputSnapshotter.OutputFileSnapshottingException;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.fingerprint.InputFingerprinter;
import org.gradle.internal.execution.fingerprint.InputFingerprinter.InputFileFingerprintingException;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.InputExecutionState;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
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
import java.io.File;
import java.util.Optional;

public class CaptureStateBeforeExecutionStep extends BuildOperationStep<PreviousExecutionContext, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureStateBeforeExecutionStep.class);

    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final OutputSnapshotter outputSnapshotter;
    private final OverlappingOutputDetector overlappingOutputDetector;
    private final Step<? super BeforeExecutionContext, ? extends CachingResult> delegate;

    public CaptureStateBeforeExecutionStep(
        BuildOperationExecutor buildOperationExecutor,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        Step<? super BeforeExecutionContext, ? extends CachingResult> delegate
    ) {
        super(buildOperationExecutor);
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.outputSnapshotter = outputSnapshotter;
        this.overlappingOutputDetector = overlappingOutputDetector;
        this.delegate = delegate;
    }

    @Override
    public CachingResult execute(UnitOfWork work, PreviousExecutionContext context) {
        Optional<BeforeExecutionState> beforeExecutionState = context.getHistory()
            .flatMap(history -> captureExecutionState(work, context));
        return delegate.execute(work, new BeforeExecutionContext() {
            @Override
            public Optional<BeforeExecutionState> getBeforeExecutionState() {
                return beforeExecutionState;
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
            public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
                return getBeforeExecutionState()
                    .map(BeforeExecutionState::getInputProperties)
                    .orElseGet(context::getInputProperties);
            }

            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                return getBeforeExecutionState()
                    .map(BeforeExecutionState::getInputFileProperties)
                    .orElseGet(context::getInputFileProperties);
            }

            @Override
            public UnitOfWork.Identity getIdentity() {
                return context.getIdentity();
            }

            @Override
            public File getWorkspace() {
                return context.getWorkspace();
            }

            @Override
            public Optional<ExecutionHistoryStore> getHistory() {
                return context.getHistory();
            }

            @Override
            public Optional<PreviousExecutionState> getPreviousExecutionState() {
                return context.getPreviousExecutionState();
            }
        });
    }

    @Nonnull
    private Optional<BeforeExecutionState> captureExecutionState(UnitOfWork work, PreviousExecutionContext context) {
        return operation(operationContext -> {
                ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots;
                try {
                    unfilteredOutputSnapshots = outputSnapshotter.snapshotOutputs(work, context.getWorkspace());
                } catch (OutputFileSnapshottingException e) {
                    work.handleUnreadableOutputs(e);
                    operationContext.setResult(Operation.Result.INSTANCE);
                    return Optional.empty();
                }

                try {
                    BeforeExecutionState executionState = captureExecutionStateWithOutputs(work, context, unfilteredOutputSnapshots);
                    operationContext.setResult(Operation.Result.INSTANCE);
                    return Optional.of(executionState);
                } catch (InputFileFingerprintingException e) {
                    // Note that we let InputFingerprintException fall through as we've already
                    // been failing for non-file value fingerprinting problems even for tasks
                    work.handleUnreadableInputs(e);
                    operationContext.setResult(Operation.Result.INSTANCE);
                    return Optional.empty();
                }
            },
            BuildOperationDescriptor
                .displayName("Snapshot inputs and outputs before executing " + work.getDisplayName())
                .details(Operation.Details.INSTANCE)
        );
    }

    private BeforeExecutionState captureExecutionStateWithOutputs(UnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, FileSystemSnapshot> unfilteredOutputSnapshots) {
        Optional<PreviousExecutionState> previousExecutionState = context.getPreviousExecutionState();

        ImplementationsBuilder implementationsBuilder = new ImplementationsBuilder(classLoaderHierarchyHasher);
        work.visitImplementations(implementationsBuilder);
        ImplementationSnapshot implementation = implementationsBuilder.getImplementation();
        ImmutableList<ImplementationSnapshot> additionalImplementations = implementationsBuilder.getAdditionalImplementations();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", work.getDisplayName(), implementation);
            LOGGER.debug("Additional implementations for {}: {}", work.getDisplayName(), additionalImplementations);
        }

        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = previousExecutionState
            .map(InputExecutionState::getInputProperties)
            .orElse(ImmutableSortedMap.of());
        ImmutableSortedMap<String, ? extends FileCollectionFingerprint> previousInputFileFingerprints = previousExecutionState
            .map(InputExecutionState::getInputFileProperties)
            .orElse(ImmutableSortedMap.of());
        ImmutableSortedMap<String, FileSystemSnapshot> previousOutputSnapshots = previousExecutionState
            .map(PreviousExecutionState::getOutputFilesProducedByWork)
            .orElse(ImmutableSortedMap.of());

        OverlappingOutputs overlappingOutputs;
        switch (work.getOverlappingOutputHandling()) {
            case DETECT_OVERLAPS:
                overlappingOutputs = overlappingOutputDetector.detect(previousOutputSnapshots, unfilteredOutputSnapshots);
                break;
            case IGNORE_OVERLAPS:
                overlappingOutputs = null;
                break;
            default:
                throw new AssertionError();
        }

        InputFingerprinter.Result newInputs = work.getInputFingerprinter().fingerprintInputProperties(
            previousInputProperties,
            previousInputFileFingerprints,
            context.getInputProperties(),
            context.getInputFileProperties(),
            work::visitRegularInputs
        );
        ImmutableSortedMap<String, ValueSnapshot> inputProperties = union(context.getInputProperties(), newInputs.getValueSnapshots());
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints = union(context.getInputFileProperties(), newInputs.getFileFingerprints());

        return new DefaultBeforeExecutionState(
            implementation,
            additionalImplementations,
            inputProperties,
            inputFileFingerprints,
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

    private static <K extends Comparable<?>, V> ImmutableSortedMap<K, V> union(
        ImmutableSortedMap<K, V> a,
        ImmutableSortedMap<K, V> b
    ) {
        if (a.isEmpty()) {
            return b;
        } else if (b.isEmpty()) {
            return a;
        } else {
            return ImmutableSortedMap.<K, V>naturalOrder()
                .putAll(a)
                .putAll(b)
                .build();
        }
    }
}
