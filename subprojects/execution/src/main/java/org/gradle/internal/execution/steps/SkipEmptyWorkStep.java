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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.ExecutionResult;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.fingerprint.InputFingerprinter;
import org.gradle.internal.execution.history.AfterExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.history.OutputsCleaner;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SkipEmptyWorkStep implements Step<PreviousExecutionContext, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipEmptyWorkStep.class);

    private final OutputChangeListener outputChangeListener;
    private final WorkInputListeners workInputListeners;
    private final Supplier<OutputsCleaner> outputsCleanerSupplier;
    private final Step<? super PreviousExecutionContext, ? extends CachingResult> delegate;

    public SkipEmptyWorkStep(
        OutputChangeListener outputChangeListener,
        WorkInputListeners workInputListeners,
        Supplier<OutputsCleaner> outputsCleanerSupplier,
        Step<? super PreviousExecutionContext, ? extends CachingResult> delegate
    ) {
        this.outputChangeListener = outputChangeListener;
        this.workInputListeners = workInputListeners;
        this.outputsCleanerSupplier = outputsCleanerSupplier;
        this.delegate = delegate;
    }

    @Override
    public CachingResult execute(UnitOfWork work, PreviousExecutionContext context) {
        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFileFingerprints = context.getInputFileProperties();
        ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots = context.getInputProperties();
        InputFingerprinter.Result newInputs = fingerprintPrimaryInputs(work, context, knownFileFingerprints, knownValueSnapshots);

        ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties = newInputs.getFileFingerprints();
        if (!sourceFileProperties.isEmpty()) {
            if (hasEmptySources(sourceFileProperties, newInputs.getPropertiesRequiringIsEmptyCheck(), work)
            ) {
                return skipExecutionWithEmptySources(work, context);
            } else {
                return executeWithNoEmptySources(work, context, newInputs.getAllFileFingerprints());
            }
        } else {
            return executeWithNoEmptySources(work, context);
        }
    }

    private boolean hasEmptySources(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties, ImmutableSet<String> propertiesRequiringIsEmptyCheck, UnitOfWork work) {
        if (propertiesRequiringIsEmptyCheck.isEmpty()) {
            return sourceFileProperties.values().stream()
                .allMatch(CurrentFileCollectionFingerprint::isEmpty);
        } else {
            // We need to check the underlying file collections for properties in propertiesRequiringIsEmptyCheck,
            // since those are backed by files which may be empty archives.
            // And being empty archives is not reflected in the fingerprint.
            return hasEmptyFingerprints(sourceFileProperties, propertyName -> !propertiesRequiringIsEmptyCheck.contains(propertyName))
                && hasEmptyInputFileCollections(work, propertiesRequiringIsEmptyCheck::contains);
        }
    }

    private boolean hasEmptyFingerprints(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties, Predicate<String> propertyNameFilter) {
        return sourceFileProperties.entrySet().stream()
            .filter(entry -> propertyNameFilter.test(entry.getKey()))
            .map(Map.Entry::getValue)
            .allMatch(CurrentFileCollectionFingerprint::isEmpty);
    }

    private boolean hasEmptyInputFileCollections(UnitOfWork work, Predicate<String> propertyNameFilter) {
        EmptyCheckingVisitor visitor = new EmptyCheckingVisitor(propertyNameFilter);
        work.visitRegularInputs(visitor);
        return visitor.isAllEmpty();
    }

    private InputFingerprinter.Result fingerprintPrimaryInputs(UnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFileFingerprints, ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots) {
        return work.getInputFingerprinter().fingerprintInputProperties(
            context.getPreviousExecutionState()
                .map(PreviousExecutionState::getInputProperties)
                .orElse(ImmutableSortedMap.of()),
            context.getPreviousExecutionState()
                .map(PreviousExecutionState::getInputFileProperties)
                .orElse(ImmutableSortedMap.of()),
            knownValueSnapshots,
            knownFileFingerprints,
            visitor -> work.visitRegularInputs(new InputFingerprinter.InputVisitor() {
                @Override
                public void visitInputFileProperty(String propertyName, InputFingerprinter.InputPropertyType type, InputFingerprinter.FileValueSupplier value) {
                    if (type == InputFingerprinter.InputPropertyType.PRIMARY) {
                        visitor.visitInputFileProperty(propertyName, type, value);
                    }
                }
            }));
    }

    @Nonnull
    private CachingResult skipExecutionWithEmptySources(UnitOfWork work, PreviousExecutionContext context) {
        ImmutableSortedMap<String, FileSystemSnapshot> outputFilesAfterPreviousExecution = context.getPreviousExecutionState()
            .map(PreviousExecutionState::getOutputFilesProducedByWork)
            .orElse(ImmutableSortedMap.of());

        ExecutionOutcome skipOutcome;
        Timer timer = Time.startTimer();
        if (outputFilesAfterPreviousExecution.isEmpty()) {
            LOGGER.info("Skipping {} as it has no source files and no previous output files.", work.getDisplayName());
            skipOutcome = ExecutionOutcome.SHORT_CIRCUITED;
        } else {
            boolean didWork = cleanPreviousTaskOutputs(outputFilesAfterPreviousExecution);
            if (didWork) {
                LOGGER.info("Cleaned previous output of {} as it has no source files.", work.getDisplayName());
                skipOutcome = ExecutionOutcome.EXECUTED_NON_INCREMENTALLY;
            } else {
                skipOutcome = ExecutionOutcome.SHORT_CIRCUITED;
            }
        }
        Duration duration = skipOutcome == ExecutionOutcome.SHORT_CIRCUITED ? Duration.ZERO : Duration.ofMillis(timer.getElapsedMillis());

        broadcastWorkInputs(work, true);

        return new CachingResult() {
            @Override
            public Duration getDuration() {
                return duration;
            }

            @Override
            public Try<ExecutionResult> getExecutionResult() {
                return Try.successful(new ExecutionResult() {
                    @Override
                    public ExecutionOutcome getOutcome() {
                        return skipOutcome;
                    }

                    @Override
                    public Object getOutput() {
                        return work.loadRestoredOutput(context.getWorkspace());
                    }
                });
            }

            @Override
            public CachingState getCachingState() {
                return CachingState.NOT_DETERMINED;
            }

            @Override
            public ImmutableList<String> getExecutionReasons() {
                return ImmutableList.of();
            }

            @Override
            public Optional<AfterExecutionState> getAfterExecutionState() {
                return Optional.empty();
            }

            @Override
            public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
                return Optional.empty();
            }
        };
    }

    private CachingResult executeWithNoEmptySources(UnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newInputFileProperties) {
        return executeWithNoEmptySources(work, new PreviousExecutionContext() {
            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                return newInputFileProperties;
            }

            @Override
            public Optional<PreviousExecutionState> getPreviousExecutionState() {
                return context.getPreviousExecutionState();
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
            public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
                return context.getInputProperties();
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
        });
    }

    private CachingResult executeWithNoEmptySources(UnitOfWork work, PreviousExecutionContext context) {
        broadcastWorkInputs(work, false);
        return delegate.execute(work, context);
    }

    private void broadcastWorkInputs(UnitOfWork work, boolean onlyPrimaryInputs) {
        workInputListeners.broadcastFileSystemInputsOf(work, onlyPrimaryInputs
            ? EnumSet.of(InputFingerprinter.InputPropertyType.PRIMARY)
            : EnumSet.allOf(InputFingerprinter.InputPropertyType.class));
    }

    private boolean cleanPreviousTaskOutputs(Map<String, FileSystemSnapshot> outputFileSnapshots) {
        OutputsCleaner outputsCleaner = outputsCleanerSupplier.get();
        for (FileSystemSnapshot outputFileSnapshot : outputFileSnapshots.values()) {
            try {
                outputChangeListener.invalidateCachesFor(SnapshotUtil.rootIndex(outputFileSnapshot).keySet());
                outputsCleaner.cleanupOutputs(outputFileSnapshot);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return outputsCleaner.getDidWork();
    }

    private static class EmptyCheckingVisitor implements InputFingerprinter.InputVisitor {
        private final Predicate<String> propertyNameFilter;
        private boolean allEmpty = true;

        public EmptyCheckingVisitor(Predicate<String> propertyNameFilter) {
            this.propertyNameFilter = propertyNameFilter;
        }

        @Override
        public void visitInputFileProperty(String propertyName, InputFingerprinter.InputPropertyType type, InputFingerprinter.FileValueSupplier value) {
            if (propertyNameFilter.test(propertyName)) {
                allEmpty = allEmpty && value.getFiles().isEmpty();
            }
        }

        public boolean isAllEmpty() {
            return allEmpty;
        }
    }
}
