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

import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInputsInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Cast;

import java.io.File;
import java.util.Set;

/**
 * A {@link TaskExecuter} which skips tasks whose source file collection is empty.
 */
public class SkipEmptySourceFilesTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = Logging.getLogger(SkipEmptySourceFilesTaskExecuter.class);
    private final TaskInputsListener taskInputsListener;
    private final TaskExecuter executer;

    public SkipEmptySourceFilesTaskExecuter(TaskInputsListener taskInputsListener, TaskExecuter executer) {
        this.taskInputsListener = taskInputsListener;
        this.executer = executer;
    }

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        TaskInputsInternal taskInputs = task.getInputs();
        if (taskInputs.getHasSourceFiles()) {
            FileCollection sourceFiles = taskInputs.getSourceFiles();
            if (sourceFiles.isEmpty()) {
                state.skipped("SKIPPED");
                TaskArtifactState taskArtifactState = context.getTaskArtifactState();
                FileCollection outputFiles = taskArtifactState.getExecutionHistory().getOutputFiles();
                if (outputFiles.isEmpty()) {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Skipping {} as it has no source files and no previous output files.", task);
                    }
                } else {
                    Set<File> outputFileSet = outputFiles.getFiles();
                    boolean deletedFiles = false;
                    for (File file : outputFileSet) {
                        if (file.isFile()) {
                            if (file.delete()) {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("Deleted stale output file '{}'.", file.getAbsolutePath());
                                }
                                deletedFiles = true;
                            } else {
                                if (LOGGER.isWarnEnabled()) {
                                    LOGGER.warn("Failed to delete stale output file '{}'.", file.getAbsolutePath());
                                }
                            }
                        }
                    }
                    if (deletedFiles) {
                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info("Cleaned previous output of {} as it has no source files.", task);
                        }
                        // uncomment the line below if you think the info that previous output has been
                        // cleaned vs. a simple SKIPPED is worthwhile.
                        //state.skipped("CLEANED");
                    }
                }
                taskInputsListener.onExecute(task, Cast.cast(FileCollectionInternal.class, sourceFiles));
                return;
            }
        }

        taskInputsListener.onExecute(task, Cast.cast(FileCollectionInternal.class, taskInputs.getFiles()));
        executer.execute(task, state, context);
    }
}
