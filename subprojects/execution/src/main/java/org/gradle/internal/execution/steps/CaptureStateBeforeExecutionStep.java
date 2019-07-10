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
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.execution.AfterPreviousExecutionContext;
import org.gradle.internal.execution.BeforeExecutionContext;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.impl.DefaultBeforeExecutionState;
import org.gradle.internal.execution.impl.OutputFilterUtil;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.AbsolutePathFingerprintingStrategy;
import org.gradle.internal.fingerprint.impl.DefaultCurrentFileCollectionFingerprint;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class CaptureStateBeforeExecutionStep implements Step<AfterPreviousExecutionContext, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaptureStateBeforeExecutionStep.class);

    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;
    private final ValueSnapshotter valueSnapshotter;
    private final Step<? super BeforeExecutionContext, ? extends CachingResult> delegate;

    public CaptureStateBeforeExecutionStep(
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        ValueSnapshotter valueSnapshotter,
        Step<? super BeforeExecutionContext, ? extends CachingResult> delegate
    ) {
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
        this.valueSnapshotter = valueSnapshotter;
        this.delegate = delegate;
    }

    @Override
    public CachingResult execute(AfterPreviousExecutionContext context) {
        BeforeExecutionState beforeExecutionState = context.getWork().isTaskHistoryMaintained()
            ? createExecutionState(context)
            : null;
        return delegate.execute(new BeforeExecutionContext() {
            @Override
            public Optional<BeforeExecutionState> getBeforeExecutionState() {
                return Optional.ofNullable(beforeExecutionState);
            }

            @Override
            public Optional<String> getRebuildReason() {
                return context.getRebuildReason();
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

    private BeforeExecutionState createExecutionState(AfterPreviousExecutionContext context) {
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
            .map(state -> state.getInputProperties())
            .orElse(ImmutableSortedMap.of());
        ImmutableSortedMap<String, FileCollectionFingerprint> previousOutputFiles = afterPreviousExecutionState
            .map(state -> state.getOutputFileProperties())
            .orElse(ImmutableSortedMap.of());

        ImmutableSortedMap<String, ValueSnapshot> inputProperties = fingerprintInputProperties(work, previousInputProperties, valueSnapshotter);
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles = fingerprintInputFiles(work);
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFiles = fingerprintOutputFiles(
            previousOutputFiles,
            work.getOutputFileSnapshotsBeforeExecution(),
            work.hasOverlappingOutputs());

        return new DefaultBeforeExecutionState(
            implementation,
            additionalImplementations,
            inputProperties,
            inputFiles,
            outputFiles
        );
    }

    private static ImmutableSortedMap<String, ValueSnapshot> fingerprintInputProperties(UnitOfWork work, ImmutableSortedMap<String, ValueSnapshot> previousSnapshots, ValueSnapshotter valueSnapshotter) {
        ImmutableSortedMap.Builder<String, ValueSnapshot> builder = ImmutableSortedMap.naturalOrder();
        work.visitInputProperties((propertyName, value) -> {
            try {
                ValueSnapshot previousSnapshot = previousSnapshots.get(propertyName);
                if (previousSnapshot == null) {
                    builder.put(propertyName, valueSnapshotter.snapshot(value));
                } else {
                    builder.put(propertyName, valueSnapshotter.snapshot(value, previousSnapshot));
                }
            } catch (Exception e) {
                throw new UncheckedIOException(String.format("Unable to store input properties for %s. Property '%s' with value '%s' cannot be serialized.",
                    work.getDisplayName(), propertyName, value), e);
            }
        });

        return builder.build();
    }

    private static ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintInputFiles(UnitOfWork work) {
        ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> builder = ImmutableSortedMap.naturalOrder();
        work.visitInputFileProperties((propertyName, value, incremental, fingerprinter) -> {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Fingerprinting property {} for {}", propertyName, work.getDisplayName());
            }
            CurrentFileCollectionFingerprint result = fingerprinter.get();
            builder.put(propertyName, result);
        });
        return builder.build();
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
}
