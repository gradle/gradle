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
import org.gradle.internal.execution.ExecutionEngine.Execution;
import org.gradle.internal.execution.ExecutionEngine.ExecutionOutcome;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.execution.history.InputExecutionState;
import org.gradle.internal.execution.history.OutputsCleaner;
import org.gradle.internal.execution.history.PreviousExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

public class SkipEmptyIncrementalWorkStep extends AbstractSkipEmptyWorkStep<PreviousExecutionContext> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipEmptyIncrementalWorkStep.class);

    private final OutputChangeListener outputChangeListener;
    private final Supplier<OutputsCleaner> outputsCleanerSupplier;

    public SkipEmptyIncrementalWorkStep(
        OutputChangeListener outputChangeListener,
        WorkInputListeners workInputListeners,
        Supplier<OutputsCleaner> outputsCleanerSupplier,
        Step<? super PreviousExecutionContext, ? extends CachingResult> delegate
    ) {
        super(workInputListeners, delegate);
        this.outputChangeListener = outputChangeListener;
        this.outputsCleanerSupplier = outputsCleanerSupplier;
    }

    @Override
    protected PreviousExecutionContext recreateContextWithNewInputFiles(PreviousExecutionContext context, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFiles) {
        return context.withInputFiles(inputFiles);
    }

    @Override
    protected ImmutableSortedMap<String, ValueSnapshot> getKnownInputProperties(PreviousExecutionContext context) {
        return context.getPreviousExecutionState()
            .map(InputExecutionState::getInputProperties)
            .orElse(ImmutableSortedMap.of());
    }

    @Override
    protected ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getKnownInputFileProperties(PreviousExecutionContext context) {
        return context.getPreviousExecutionState()
            .map(InputExecutionState::getInputFileProperties)
            .orElse(ImmutableSortedMap.of());
    }

    @Override
    protected CachingResult performSkip(UnitOfWork work, PreviousExecutionContext context) {
        ImmutableSortedMap<String, FileSystemSnapshot> outputFilesAfterPreviousExecution = context.getPreviousExecutionState()
            .map(PreviousExecutionState::getOutputFilesProducedByWork)
            .orElse(ImmutableSortedMap.of());

        ExecutionOutcome skipOutcome;
        Timer timer = Time.startTimer();
        String executionReason = null;
        if (outputFilesAfterPreviousExecution.isEmpty()) {
            LOGGER.info("Skipping {} as it has no source files and no previous output files.", work.getDisplayName());
            skipOutcome = ExecutionOutcome.SHORT_CIRCUITED;
        } else {
            boolean didWork = cleanPreviousOutputs(outputFilesAfterPreviousExecution);
            if (didWork) {
                LOGGER.info("Cleaned previous output of {} as it has no source files.", work.getDisplayName());
                skipOutcome = ExecutionOutcome.EXECUTED_NON_INCREMENTALLY;
                executionReason = "Cleaned previous output";
            } else {
                skipOutcome = ExecutionOutcome.SHORT_CIRCUITED;
            }
        }
        Duration duration = skipOutcome == ExecutionOutcome.SHORT_CIRCUITED ? Duration.ZERO : Duration.ofMillis(timer.getElapsedMillis());

        return CachingResult.shortcutResult(Execution.skipped(skipOutcome, work), executionReason, duration);
    }

    private boolean cleanPreviousOutputs(Map<String, FileSystemSnapshot> outputFileSnapshots) {
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
}
