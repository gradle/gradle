/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.execution;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.exceptions.FailureResolutionAware;
import org.gradle.api.InvalidUserDataException;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.internal.logging.text.StyledTextOutput;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

/**
 * A {@code TaskSelectionException} is thrown when the tasks to execute cannot be selected due to some user input problem.
 */
public class TaskSelectionException extends InvalidUserDataException implements FailureResolutionAware {
    public TaskSelectionException(String message) {
        super(message);
    }

    @Override
    public void appendResolution(StyledTextOutput output, BuildClientMetaData clientMetaData) {
        output.text("Run ");
        clientMetaData.describeCommand(output.withStyle(UserInput), ProjectInternal.TASKS_TASK);
        output.text(" to get a list of available tasks.");
    }
}
