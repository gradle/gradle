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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.execution.internal.TaskInputsListeners;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.Cast;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.internal.execution.ExecutionOutcome;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.impl.OutputsCleaner;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;

public class DefaultEmptySourceTaskSkipper implements EmptySourceTaskSkipper {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultEmptySourceTaskSkipper.class);

    private final BuildOutputCleanupRegistry buildOutputCleanupRegistry;
    private final Deleter deleter;
    private final OutputChangeListener outputChangeListener;
    private final TaskInputsListeners taskInputsListeners;

    public DefaultEmptySourceTaskSkipper(
        BuildOutputCleanupRegistry buildOutputCleanupRegistry,
        Deleter deleter,
        OutputChangeListener outputChangeListener,
        TaskInputsListeners taskInputsListeners
    ) {
        this.buildOutputCleanupRegistry = buildOutputCleanupRegistry;
        this.deleter = deleter;
        this.outputChangeListener = outputChangeListener;
        this.taskInputsListeners = taskInputsListeners;
    }

    @Override
    public Optional<ExecutionOutcome> skipIfEmptySources(
        TaskInternal task,
        boolean hasSourceFiles,
        FileCollection inputFiles,
        FileCollection sourceFiles,
        Map<String, FileCollectionFingerprint> outputFileSnapshots
    ) {
        if (hasSourceFiles && sourceFiles.isEmpty()) {
            ExecutionOutcome skipOutcome = skipOutcomeFor(task, outputFileSnapshots);
            broadcastFileSystemInputsOf(task, sourceFiles);
            return Optional.of(skipOutcome);
        } else {
            broadcastFileSystemInputsOf(task, inputFiles);
            return Optional.empty();
        }
    }

    private ExecutionOutcome skipOutcomeFor(TaskInternal task, Map<String, FileCollectionFingerprint> outputFileSnapshots) {
        if (outputFileSnapshots.isEmpty()) {
            LOGGER.info("Skipping {} as it has no source files and no previous output files.", task);
            return ExecutionOutcome.SHORT_CIRCUITED;
        }

        boolean didWork = cleanPreviousTaskOutputs(outputFileSnapshots);
        if (didWork) {
            LOGGER.info("Cleaned previous output of {} as it has no source files.", task);
            return ExecutionOutcome.EXECUTED_NON_INCREMENTALLY;
        }

        return ExecutionOutcome.SHORT_CIRCUITED;
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

    private void broadcastFileSystemInputsOf(TaskInternal task, FileCollection fileSystemInputs) {
        taskInputsListeners.broadcastFileSystemInputsOf(
            task,
            Cast.cast(FileCollectionInternal.class, fileSystemInputs)
        );
    }
}
