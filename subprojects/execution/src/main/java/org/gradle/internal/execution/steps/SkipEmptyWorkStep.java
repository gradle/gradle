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
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.internal.Try;
import org.gradle.internal.execution.AfterPreviousExecutionContext;
import org.gradle.internal.execution.BuildOutputCleanupRegistry;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.Step;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.caching.CachingState;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionState;
import org.gradle.internal.execution.impl.OutputsCleaner;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.snapshot.ValueSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

import static org.gradle.internal.execution.InputFingerprinter.union;

public class SkipEmptyWorkStep implements Step<AfterPreviousExecutionContext, CachingResult> {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkipEmptyWorkStep.class);

    private final BuildOutputCleanupRegistry buildOutputCleanupRegistry;
    private final Deleter deleter;
    private final InputFingerprinter inputFingerprinter;
    private final OutputChangeListener outputChangeListener;
    private final Step<? super AfterPreviousExecutionContext, ? extends CachingResult> delegate;

    public SkipEmptyWorkStep(
        BuildOutputCleanupRegistry buildOutputCleanupRegistry,
        Deleter deleter,
        InputFingerprinter inputFingerprinter,
        OutputChangeListener outputChangeListener,
        Step<? super AfterPreviousExecutionContext, ? extends CachingResult> delegate
    ) {
        this.buildOutputCleanupRegistry = buildOutputCleanupRegistry;
        this.deleter = deleter;
        this.inputFingerprinter = inputFingerprinter;
        this.outputChangeListener = outputChangeListener;
        this.delegate = delegate;
    }

    @Override
    public CachingResult execute(UnitOfWork work, AfterPreviousExecutionContext context) {
        InputFingerprinter.Result sourceInputs = inputFingerprinter.fingerprintInputProperties(
            work,
            context.getAfterPreviousExecutionState()
                .map(ExecutionState::getInputProperties)
                .orElse(ImmutableSortedMap.of()),
            context.getInputProperties(),
            context.getInputFileProperties(),
            (propertyName, type, identity) -> type.isSkipWhenEmpty());

        // We don't currently have any non-file source properties
        assert sourceInputs.getValueSnapshots().isEmpty();

        if (!sourceInputs.getFileFingerprints().isEmpty()) {
            ImmutableSortedMap<String, ValueSnapshot> inputProperties = union(context.getInputProperties(), sourceInputs.getValueSnapshots());
            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties = union(context.getInputFileProperties(), sourceInputs.getFileFingerprints());

            if (sourceInputs.getFileFingerprints().values().stream()
                .allMatch(CurrentFileCollectionFingerprint::isEmpty)
            ) {
                return skipExecutionWithEmptySources(work, context);
            } else {
                return executeWithNoEmptySources(work, withSources(context, inputProperties, inputFileProperties));
            }
        } else {
            return executeWithNoEmptySources(work, context);
        }
    }

    @Nonnull
    private CachingResult skipExecutionWithEmptySources(UnitOfWork work, AfterPreviousExecutionContext context) {
        work.getHistory()
            .ifPresent(history -> history.remove(context.getIdentity().getUniqueId()));

        ImmutableSortedMap<String, FileCollectionFingerprint> outputFilesAfterPreviousExecution = context.getAfterPreviousExecutionState()
            .map(AfterPreviousExecutionState::getOutputFileProperties)
            .orElse(ImmutableSortedMap.of());

        ExecutionOutcome skipOutcome;
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

        work.broadcastRelevantFileSystemInputs(skipOutcome);

        return new CachingResult() {
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
            public ImmutableSortedMap<String, ? extends FileCollectionFingerprint> getFinalOutputs() {
                return ImmutableSortedMap.of();
            }

            @Override
            public Optional<OriginMetadata> getReusedOutputOriginMetadata() {
                return Optional.empty();
            }
        };
    }

    private CachingResult executeWithNoEmptySources(UnitOfWork work, AfterPreviousExecutionContext context) {
        work.broadcastRelevantFileSystemInputs(null);
        return delegate.execute(work, context);
    }

    private static AfterPreviousExecutionContext withSources(AfterPreviousExecutionContext context, ImmutableSortedMap<String, ValueSnapshot> inputProperties, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> inputFileProperties) {
        return new AfterPreviousExecutionContext() {
            @Override
            public Optional<AfterPreviousExecutionState> getAfterPreviousExecutionState() {
                return context.getAfterPreviousExecutionState();
            }

            @Override
            public File getWorkspace() {
                return context.getWorkspace();
            }

            @Override
            public ImmutableSortedMap<String, ValueSnapshot> getInputProperties() {
                return inputProperties;
            }

            @Override
            public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getInputFileProperties() {
                return inputFileProperties;
            }

            @Override
            public UnitOfWork.Identity getIdentity() {
                return context.getIdentity();
            }

            @Override
            public Optional<String> getRebuildReason() {
                return context.getRebuildReason();
            }
        };
    }

    private boolean cleanPreviousTaskOutputs(Map<String, FileCollectionFingerprint> outputFileSnapshots) {
        OutputsCleaner outputsCleaner = new OutputsCleaner(
            deleter,
            buildOutputCleanupRegistry::isOutputOwnedByBuild,
            buildOutputCleanupRegistry::isOutputOwnedByBuild
        );
        for (FileCollectionFingerprint outputFingerprints : outputFileSnapshots.values()) {
            try {
                outputChangeListener.beforeOutputChange(outputFingerprints.getRootPaths());
                outputsCleaner.cleanupOutputs(outputFingerprints);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return outputsCleaner.getDidWork();
    }
}
