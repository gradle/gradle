/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Cast;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.util.GFileUtils;

import java.io.File;

/**
 * A {@link TaskExecuter} which skips tasks whose source file collection is empty.
 */
public class SkipEmptySourceFilesTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = Logging.getLogger(SkipEmptySourceFilesTaskExecuter.class);
    private final TaskInputsListener taskInputsListener;
    private final BuildOutputCleanupRegistry buildOutputCleanupRegistry;
    private final OutputChangeListener outputChangeListener;
    private final TaskExecuter executer;
    private final ExecutionHistoryStore executionHistoryStore;

    public SkipEmptySourceFilesTaskExecuter(TaskInputsListener taskInputsListener, ExecutionHistoryStore executionHistoryStore, BuildOutputCleanupRegistry buildOutputCleanupRegistry, OutputChangeListener outputChangeListener, TaskExecuter executer) {
        this.taskInputsListener = taskInputsListener;
        this.executionHistoryStore = executionHistoryStore;
        this.buildOutputCleanupRegistry = buildOutputCleanupRegistry;
        this.outputChangeListener = outputChangeListener;
        this.executer = executer;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, final TaskExecutionContext context) {
        TaskProperties properties = context.getTaskProperties();
        FileCollection sourceFiles = properties.getSourceFiles();
        if (properties.hasSourceFiles() && sourceFiles.isEmpty()) {
            AfterPreviousExecutionState previousExecution = context.getAfterPreviousExecution();
            @SuppressWarnings("RedundantTypeArguments")
            ImmutableSortedMap<String, FileCollectionFingerprint> outputFiles = previousExecution == null
                ? ImmutableSortedMap.<String, FileCollectionFingerprint>of()
                : previousExecution.getOutputFileProperties();
            if (outputFiles.isEmpty()) {
                state.setOutcome(TaskExecutionOutcome.NO_SOURCE);
                LOGGER.info("Skipping {} as it has no source files and no previous output files.", task);
            } else {
                boolean cleanupDirectories = !context.getOverlappingOutputs().isPresent();
                if (!cleanupDirectories) {
                    LOGGER.info("No leftover directories for {} will be deleted since overlapping outputs were detected.", task);
                }
                outputChangeListener.beforeOutputChange();
                boolean deletedFiles = false;
                boolean debugEnabled = LOGGER.isDebugEnabled();

                for (FileCollectionFingerprint outputFingerprints : outputFiles.values()) {
                    for (String outputPath : outputFingerprints.getFingerprints().keySet()) {
                        File file = new File(outputPath);
                        if (file.exists() && buildOutputCleanupRegistry.isOutputOwnedByBuild(file)) {
                            if (!cleanupDirectories && file.isDirectory()) {
                                continue;
                            }
                            if (debugEnabled) {
                                LOGGER.debug("Deleting stale output file '{}'.", file.getAbsolutePath());
                            }
                            GFileUtils.forceDelete(file);
                            deletedFiles = true;
                        }
                    }
                }
                if (deletedFiles) {
                    LOGGER.info("Cleaned previous output of {} as it has no source files.", task);
                    state.setOutcome(TaskExecutionOutcome.EXECUTED);
                } else {
                    state.setOutcome(TaskExecutionOutcome.NO_SOURCE);
                }
            }
            taskInputsListener.onExecute(task, Cast.cast(FileCollectionInternal.class, sourceFiles));
            executionHistoryStore.remove(task.getPath());
            return TaskExecuterResult.WITHOUT_OUTPUTS;
        } else {
            taskInputsListener.onExecute(task, Cast.cast(FileCollectionInternal.class, properties.getInputFiles()));
        }
        return executer.execute(task, state, context);
    }
}
