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
import org.gradle.internal.Try;
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.UnitOfWork.InputFileValueSupplier;
import org.gradle.internal.execution.UnitOfWork.InputVisitor;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.OutputsCleaner;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
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
        if (sourceFileProperties.isEmpty()) {
            return executeWithNonEmptySources(work, context);
        } else {
            if (hasEmptySources(sourceFileProperties, newInputs.getPropertiesRequiringIsEmptyCheck(), work)) {
                return skipExecutionWithEmptySources(work, context);
            } else {
                return executeWithNonEmptySources(work, context.withInputFiles(newInputs.getAllFileFingerprints()));
            }
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
            visitor -> work.visitRegularInputs(new InputVisitor() {
                @Override
                public void visitInputFileProperty(String propertyName, InputBehavior behavior, InputFileValueSupplier value) {
                    if (behavior.shouldSkipWhenEmpty()) {
                        visitor.visitInputFileProperty(propertyName, behavior, value);
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

        broadcastWorkInputs(context.getIdentity(), work, true);

        Try<Execution> execution = Try.successful(new Execution() {
            @Override
            public ExecutionOutcome getOutcome() {
                return skipOutcome;
            }

            @Override
            public Object getOutput() {
                return work.loadAlreadyProducedOutput(context.getWorkspace());
            }
        });
        return new CachingResult(duration, execution, null, ImmutableList.of(), null, CachingState.NOT_DETERMINED);
    }

    private CachingResult executeWithNonEmptySources(UnitOfWork work, PreviousExecutionContext context) {
        broadcastWorkInputs(context.getIdentity(), work, false);
        return delegate.execute(work, context);
    }

    private void broadcastWorkInputs(UnitOfWork.Identity identity, UnitOfWork work, boolean onlyPrimaryInputs) {
        workInputListeners.broadcastFileSystemInputsOf(identity, work, onlyPrimaryInputs
            ? EnumSet.of(InputBehavior.PRIMARY)
            : EnumSet.allOf(InputBehavior.class));
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

    private static class EmptyCheckingVisitor implements InputVisitor {
        private final Predicate<String> propertyNameFilter;
        private boolean allEmpty = true;

        public EmptyCheckingVisitor(Predicate<String> propertyNameFilter) {
            this.propertyNameFilter = propertyNameFilter;
        }

        @Override
        public void visitInputFileProperty(String propertyName, InputBehavior behavior, InputFileValueSupplier value) {
            if (propertyNameFilter.test(propertyName)) {
                allEmpty = allEmpty && value.getFiles().isEmpty();
            }
        }

        public boolean isAllEmpty() {
            return allEmpty;
        }
    }
}
