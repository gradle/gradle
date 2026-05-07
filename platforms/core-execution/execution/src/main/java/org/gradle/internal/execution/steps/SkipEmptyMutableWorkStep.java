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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.execution.Execution;
import org.gradle.internal.execution.Execution.ExecutionOutcome;
import org.gradle.internal.execution.ExecutionProblemHandler;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.InputVisitor;
import org.gradle.internal.execution.MutableUnitOfWork;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.history.ExecutionInputState;
import org.gradle.internal.execution.history.OutputsCleaner;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.properties.InputBehavior;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class SkipEmptyMutableWorkStep extends MutableStep<PreviousExecutionContext, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipEmptyMutableWorkStep.class);

    private final ExecutionProblemHandler problemHandler;
    private final WorkInputListeners workInputListeners;
    private final OutputChangeListener outputChangeListener;
    private final Supplier<OutputsCleaner> outputsCleanerSupplier;

    protected final Step<? super PreviousExecutionContext, ? extends CachingResult> delegate;

    public SkipEmptyMutableWorkStep(
        ExecutionProblemHandler problemHandler,
        OutputChangeListener outputChangeListener,
        WorkInputListeners workInputListeners,
        Supplier<OutputsCleaner> outputsCleanerSupplier,
        Step<? super PreviousExecutionContext, ? extends CachingResult> delegate
    ) {
        this.problemHandler = problemHandler;
        this.workInputListeners = workInputListeners;
        this.outputChangeListener = outputChangeListener;
        this.outputsCleanerSupplier = outputsCleanerSupplier;
        this.delegate = delegate;
    }

    @Override
    protected CachingResult executeMutable(MutableUnitOfWork work, PreviousExecutionContext context) {
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
                return executeWithNonEmptySources(work, recreateContextWithNewInputFiles(context, newInputs.getAllFileFingerprints()));
            }
        }
    }

    private static boolean hasEmptySources(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties, ImmutableSet<String> propertiesRequiringIsEmptyCheck, UnitOfWork work) {
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

    private static boolean hasEmptyFingerprints(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> sourceFileProperties, Predicate<String> propertyNameFilter) {
        return sourceFileProperties.entrySet().stream()
            .filter(entry -> propertyNameFilter.test(entry.getKey()))
            .map(Map.Entry::getValue)
            .allMatch(CurrentFileCollectionFingerprint::isEmpty);
    }

    private static boolean hasEmptyInputFileCollections(UnitOfWork work, Predicate<String> propertyNameFilter) {
        EmptyCheckingVisitor visitor = new EmptyCheckingVisitor(propertyNameFilter);
        work.visitMutableInputs(visitor);
        return visitor.isAllEmpty();
    }

    protected PreviousExecutionContext recreateContextWithNewInputFiles(PreviousExecutionContext context, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles) {
        return new PreviousExecutionContext(context) {
            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                return inputFiles;
            }
        };
    }

    protected ImmutableSortedMap<String, ValueSnapshot> getKnownInputProperties(PreviousExecutionContext context) {
        return context.getPreviousExecutionState()
            .map(ExecutionInputState::getInputProperties)
            .orElse(ImmutableSortedMap.of());
    }

    protected ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getKnownInputFileProperties(PreviousExecutionContext context) {
        return context.getPreviousExecutionState()
            .map(ExecutionInputState::getInputFileProperties)
            .orElse(ImmutableSortedMap.of());
    }

    protected CachingResult performSkip(UnitOfWork work, PreviousExecutionContext context) {
        ImmutableSortedMap<String, FileSystemSnapshot> outputFilesAfterPreviousExecution = context.getPreviousExecutionState()
            .map(PreviousExecutionState::getOutputFilesProducedByWork)
            .orElse(ImmutableSortedMap.of());

        ExecutionOutcome skipOutcome;
        Timer timer = Time.startTimer();
        String executionReason = null;
        if (outputFilesAfterPreviousExecution.isEmpty()) {
            LOGGER.info("Skipping {} as it has no source files and no previous output files.", work.getDisplayName());
            skipOutcome = Execution.ExecutionOutcome.SHORT_CIRCUITED;
        } else {
            boolean didWork = cleanPreviousOutputs(outputFilesAfterPreviousExecution);
            if (didWork) {
                LOGGER.info("Cleaned previous output of {} as it has no source files.", work.getDisplayName());
                skipOutcome = Execution.ExecutionOutcome.EXECUTED_NON_INCREMENTALLY;
                executionReason = "Cleaned previous output";
            } else {
                skipOutcome = Execution.ExecutionOutcome.SHORT_CIRCUITED;
            }
        }
        Duration duration = skipOutcome == Execution.ExecutionOutcome.SHORT_CIRCUITED ? Duration.ZERO : Duration.ofMillis(timer.getElapsedMillis());
        return CachingResult.shortcutResult(duration, Execution.skipped(skipOutcome, work), null, executionReason, null);
    }

    private boolean cleanPreviousOutputs(Map<String, FileSystemSnapshot> outputFileSnapshots) {
        OutputsCleaner outputsCleaner = outputsCleanerSupplier.get();
        for (FileSystemSnapshot outputFileSnapshot : outputFileSnapshots.values()) {
            try {
                outputChangeListener.invalidateCachesFor(SnapshotUtil.rootIndex(outputFileSnapshot).keySet());
                outputsCleaner.cleanupOutputs(outputFileSnapshot);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        return outputsCleaner.getDidWork();
    }

    private InputFingerprinter.Result fingerprintPrimaryInputs(UnitOfWork work, PreviousExecutionContext context, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> knownFileFingerprints, ImmutableSortedMap<String, ValueSnapshot> knownValueSnapshots) {
        return work.getInputFingerprinter().fingerprintInputProperties(
            getKnownInputProperties(context),
            getKnownInputFileProperties(context),
            knownValueSnapshots,
            knownFileFingerprints,
            visitor -> work.visitMutableInputs(new InputVisitor() {
                @Override
                public void visitInputFileProperty(String propertyName, InputBehavior behavior, InputFileValueSupplier value) {
                    if (behavior.shouldSkipWhenEmpty()) {
                        visitor.visitInputFileProperty(propertyName, behavior, value);
                    }
                }
            }),
            work.getInputDependencyChecker(context.getValidationContext()));
    }

    private CachingResult skipExecutionWithEmptySources(UnitOfWork work, PreviousExecutionContext context) {
        // Make sure we check for missing dependencies even if we skip executing the work
        WorkValidationContext validationContext = context.getValidationContext();
        work.checkOutputDependencies(validationContext);
        problemHandler.handleReportedProblems(context.getIdentity(), work, validationContext);

        CachingResult result = performSkip(work, context);
        workInputListeners.broadcastFileSystemInputsOf(work, EnumSet.of(InputBehavior.PRIMARY));
        return result;
    }

    private CachingResult executeWithNonEmptySources(UnitOfWork work, PreviousExecutionContext context) {
        workInputListeners.broadcastFileSystemInputsOf(work, EnumSet.allOf(InputBehavior.class));
        return delegate.execute(work, context);
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
