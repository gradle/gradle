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
import com.google.common.collect.Maps;
import org.gradle.internal.execution.AfterPreviousExecutionContext;
import org.gradle.internal.execution.BeforeExecutionContext;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.ExecutionState;
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState;
import org.gradle.internal.execution.impl.OutputFilterUtil;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputDetector;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationType;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.gradle.internal.execution.UnitOfWork.IdentityKind.NON_IDENTITY;
import static org.gradle.internal.execution.impl.InputFingerprintUtil.fingerprintInputFiles;
import static org.gradle.internal.execution.impl.InputFingerprintUtil.fingerprintInputProperties;

public class CaptureStateBeforeExecutionStep extends BuildOperationStep<AfterPreviousExecutionContext, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureStateBeforeExecutionStep.class);

    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final OutputSnapshotter outputSnapshotter;
    private final OverlappingOutputDetector overlappingOutputDetector;
    private final ValueSnapshotter valueSnapshotter;
    private final Step<? super BeforeExecutionContext, ? extends CachingResult> delegate;

    public CaptureStateBeforeExecutionStep(
        BuildOperationExecutor buildOperationExecutor,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        OutputSnapshotter outputSnapshotter,
        OverlappingOutputDetector overlappingOutputDetector,
        ValueSnapshotter valueSnapshotter,
        Step<? super BeforeExecutionContext, ? extends CachingResult> delegate
    ) {
        super(buildOperationExecutor);
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.outputSnapshotter = outputSnapshotter;
        this.valueSnapshotter = valueSnapshotter;
        this.overlappingOutputDetector = overlappingOutputDetector;
        this.delegate = delegate;
    }

    @Override
    public CachingResult execute(AfterPreviousExecutionContext context) {
        Optional<BeforeExecutionState> beforeExecutionState = context.getWork().getHistory()
            .map(executionHistoryStore -> captureExecutionStateOp(context));
        return delegate.execute(new BeforeExecutionContext() {
            @Override
            public Optional<BeforeExecutionState> getBeforeExecutionState() {
                return beforeExecutionState;
            }

            @Override
            public Optional<String> getRebuildReason() {
                return context.getRebuildReason();
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
            public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                return context.getAfterPreviousExecutionState();
            }

            @Override
            public UnitOfWork getWork() {
                return context.getWork();
            }
        });
    }

    private BeforeExecutionState captureExecutionStateOp(AfterPreviousExecutionContext executionContext) {
        return operation(operationContext -> {
                BeforeExecutionState beforeExecutionState = captureExecutionState(executionContext);
                operationContext.setResult(Operation.Result.INSTANCE);
                return beforeExecutionState;
            },
            BuildOperationDescriptor
                .displayName("Snapshot inputs and outputs before executing " + executionContext.getWork().getDisplayName())
                .details(Operation.Details.INSTANCE)
        );
    }

    private BeforeExecutionState captureExecutionState(AfterPreviousExecutionContext context) {
        Optional<AfterPreviousExecutionState> afterPreviousExecutionState = context.getAfterPreviousExecutionState();
        UnitOfWork work = context.getWork();

        ImplementationsBuilder implementationsBuilder = new ImplementationsBuilder(classLoaderHierarchyHasher);
        work.visitImplementations(implementationsBuilder);
        ImplementationSnapshot implementation = implementationsBuilder.getImplementation();
        ImmutableList<ImplementationSnapshot> additionalImplementations = implementationsBuilder.getAdditionalImplementations();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Implementation for {}: {}", work.getDisplayName(), implementation);
            LOGGER.debug("Additional implementations for {}: {}", work.getDisplayName(), additionalImplementations);
        }

        ImmutableSortedMap<String, ValueSnapshot> previousInputProperties = afterPreviousExecutionState
            .map(ExecutionState::getInputProperties)
            .orElse(ImmutableSortedMap.of());
        ImmutableSortedMap<String, FileCollectionFingerprint> outputSnapshotsAfterPreviousExecution = afterPreviousExecutionState
            .map(AfterPreviousExecutionState::getOutputFileProperties)
            .orElse(ImmutableSortedMap.of());

        ImmutableSortedMap<String, FileSystemSnapshot> outputFileSnapshots = outputSnapshotter.snapshotOutputs(work, context.getWorkspace());

        OverlappingOutputs overlappingOutputs;
        switch (work.getOverlappingOutputHandling()) {
            case DETECT_OVERLAPS:
                overlappingOutputs = overlappingOutputDetector.detect(outputSnapshotsAfterPreviousExecution, outputFileSnapshots);
                break;
            case IGNORE_OVERLAPS:
                overlappingOutputs = null;
                break;
            default:
                throw new AssertionError();
        }

        ImmutableSortedMap<String, ValueSnapshot> inputProperties = fingerprintInputProperties(
            work,
            previousInputProperties,
            valueSnapshotter,
            context.getInputProperties(),
            (propertyName, identity) -> identity == NON_IDENTITY);
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileFingerprints = fingerprintInputFiles(
            work,
            context.getInputFileProperties(),
            (propertyName, type, identity) -> identity == NON_IDENTITY);
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFileFingerprints = fingerprintOutputFiles(
            outputSnapshotsAfterPreviousExecution,
            outputFileSnapshots,
            overlappingOutputs != null);

        return new DefaultBeforeExecutionState(
            implementation,
            additionalImplementations,
            inputProperties,
            inputFileFingerprints,
            outputFileFingerprints,
            outputFileSnapshots,
            overlappingOutputs
        );
    }

    private static ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintOutputFiles(
        ImmutableSortedMap<String, FileCollectionFingerprint> previousOutputFingerprints,
        ImmutableSortedMap<String, FileSystemSnapshot> beforeExecutionOutputSnapshots,
        boolean hasOverlappingOutputs
    ) {
        return ImmutableSortedMap.copyOfSorted(
            Maps.transformEntries(beforeExecutionOutputSnapshots, (key, outputSnapshot) -> {
                    FileCollectionFingerprint previousOutputFingerprint = previousOutputFingerprints.get(key);
                    //noinspection ConstantConditions
                    return previousOutputFingerprint == null
                        ? AbsolutePathFingerprintingStrategy.IGNORE_MISSING.getEmptyFingerprint()
                        : fingerprintOutputSnapshot(outputSnapshot, previousOutputFingerprint, hasOverlappingOutputs);
                }
            )
        );
    }

    private static CurrentFileCollectionFingerprint fingerprintOutputSnapshot(FileSystemSnapshot beforeExecutionOutputSnapshots, FileCollectionFingerprint previousOutputFingerprint, boolean hasOverlappingOutputs) {
        List<FileSystemSnapshot> roots = hasOverlappingOutputs
            ? OutputFilterUtil.filterOutputSnapshotBeforeExecution(previousOutputFingerprint, beforeExecutionOutputSnapshots)
            : ImmutableList.of(beforeExecutionOutputSnapshots);
        return DefaultCurrentFileCollectionFingerprint.from(roots, AbsolutePathFingerprintingStrategy.IGNORE_MISSING);
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
            if (this.implementation != null) {
                throw new IllegalStateException("Implementation already set");
            }
            this.implementation = implementation;
        }

        @Override
        public void visitAdditionalImplementation(ImplementationSnapshot implementation) {
            additionalImplementations.add(implementation);
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
