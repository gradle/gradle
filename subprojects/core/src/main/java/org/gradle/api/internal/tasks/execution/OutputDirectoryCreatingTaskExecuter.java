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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.OutputType;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;

import static org.gradle.util.GFileUtils.mkdirs;

/**
 * A {@link TaskExecuter} which creates directories for task outputs.
 */
public class OutputDirectoryCreatingTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = Logging.getLogger(OutputDirectoryCreatingTaskExecuter.class);
    private final TaskExecuter executer;

    public OutputDirectoryCreatingTaskExecuter(TaskExecuter executer) {
        this.executer = executer;
    }

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        for (TaskOutputFilePropertySpec outputProperty : task.getOutputs().getFileProperties()) {
            OutputType type = outputProperty.getOutputType();
            for (File output : outputProperty.getPropertyFiles()) {
                ensureOutput(outputProperty, output, type);
            }
        }

        executer.execute(task, state, context);
    }

    private static void ensureOutput(TaskOutputFilePropertySpec outputProperty, File output, OutputType type) {
        if (output == null) {
            LOGGER.debug("Not ensuring directory exists for property {}, because value is null", outputProperty);
            return;
        }
        switch (type) {
            case DIRECTORY:
                LOGGER.debug("Ensuring directory exists for property {} at {}", outputProperty, output);
                mkdirs(output);
                break;
            case FILE:
                LOGGER.debug("Ensuring parent directory exists for property {} at {}", outputProperty, output);
                mkdirs(output.getParentFile());
                break;
            default:
                throw new AssertionError();
        }
    }
}
