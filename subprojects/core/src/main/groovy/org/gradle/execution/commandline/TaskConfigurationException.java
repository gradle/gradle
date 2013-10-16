/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.execution.commandline;

import org.gradle.FailureResolutionAware;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.cli.CommandLineArgumentException;
import org.gradle.configuration.ImplicitTasksConfigurer;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.logging.StyledTextOutput;

import static org.gradle.logging.StyledTextOutput.Style.UserInput;

public class TaskConfigurationException extends GradleException implements FailureResolutionAware {

    private final Task task;

    public TaskConfigurationException(Task task, String message, CommandLineArgumentException cause) {
        super(message, cause);
        this.task = task;
    }

    public void appendResolution(StyledTextOutput output, BuildClientMetaData clientMetaData) {
        output.text("Run ");
        clientMetaData.describeCommand(output.withStyle(UserInput), ImplicitTasksConfigurer.HELP_TASK);
        output.withStyle(UserInput).format(" --task %s", task.getName());
        output.text(" to get task usage details. ");
    }
}
