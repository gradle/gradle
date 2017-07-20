/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.util.GFileUtils;

import java.io.File;

public class CleanupStaleOutputsExecuter implements TaskExecuter {
    private final TaskExecuter executer;
    private final TaskHistoryRepository taskHistoryRepository;
    private final BuildOutputCleanupRegistry cleanupRegistry;

    public CleanupStaleOutputsExecuter(BuildOutputCleanupRegistry cleanupRegistry, TaskExecuter executer, TaskHistoryRepository taskHistoryRepository) {
        this.cleanupRegistry = cleanupRegistry;
        this.executer = executer;
        this.taskHistoryRepository = taskHistoryRepository;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        for (TaskOutputFilePropertySpec outputFileSpec : task.getOutputs().getFileProperties()) {
            FileCollection files = outputFileSpec.getPropertyFiles();
            for (File file : files) {
                if (file.exists() && !generatedByGradle(file, task) && isSaveToDelete(file)) {
                    GFileUtils.forceDelete(file);
                }
            }
        }
        executer.execute(task, state, context);
    }

    private boolean isSaveToDelete(File file) {
        return true;
//        return cleanupRegistry.getOutputs().contains(file);
    }

    private boolean generatedByGradle(File file, TaskInternal task) {
        TaskExecution previousExecution = taskHistoryRepository.getHistory(task).getPreviousExecution();
        if (previousExecution == null) {
            return false;
        }
        for (FileCollectionSnapshot fileCollectionSnapshot : previousExecution.getOutputFilesSnapshot().values()) {
            if (fileCollectionSnapshot.getElements().contains(file)) {
                return true;
            }
        }
        return false;
    }
}
