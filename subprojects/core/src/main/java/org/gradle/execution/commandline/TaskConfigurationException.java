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

import org.gradle.api.GradleException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.FailureResolutionAware;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

@Contextual
public class TaskConfigurationException extends GradleException implements FailureResolutionAware {

    private final String taskPath;

    public TaskConfigurationException(String taskPath, String message, Exception cause) {
        super(message, cause);
        this.taskPath = taskPath;
    }

    @Override
    public void appendResolutions(Context context) {
        context.appendResolution(output -> {
            output.text("Run ");
            context.getClientMetaData().describeCommand(output.withStyle(UserInput), ProjectInternal.HELP_TASK);
            output.withStyle(UserInput).format(" --task %s", taskPath);
            output.text(" to get task usage details.");
        });
    }
}
