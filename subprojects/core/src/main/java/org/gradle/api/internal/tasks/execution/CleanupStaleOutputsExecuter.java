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
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.internal.cleanup.BuildOutputCleanupRegistry;
import org.gradle.util.GFileUtils;

import java.io.File;

public class CleanupStaleOutputsExecuter implements TaskExecuter {
    private final TaskExecuter executer;
    private final BuildOutputCleanupRegistry cleanupRegistry;

    public CleanupStaleOutputsExecuter(BuildOutputCleanupRegistry cleanupRegistry, TaskExecuter executer) {
        this.cleanupRegistry = cleanupRegistry;
        this.executer = executer;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        for (TaskOutputFilePropertySpec outputFileSpec : task.getOutputs().getFileProperties()) {
            FileCollection files = outputFileSpec.getPropertyFiles();
            for (File file : files) {
                if (file.exists() && !generatedByGradle(file, context.getTaskArtifactState()) && isSaveToDelete(file)) {
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

    private boolean generatedByGradle(File file, TaskArtifactState taskArtifactState) {
        return taskArtifactState.getExecutionHistory().getOutputFiles().contains(file);
    }
}
