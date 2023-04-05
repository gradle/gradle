/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.exceptions.FailureResolutionAware;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

abstract class BaseSelectionException extends InvalidUserDataException implements FailureResolutionAware {
    private final String taskName;
    private final String targetName;

    public BaseSelectionException(String message, String taskName, String targetName) {
        super(message);
        this.taskName = taskName;
        this.targetName = targetName;
    }

    @Override
    public void appendResolutions(Context context) {
        context.appendResolution(output -> {
            output.text("Run ");
            context.getClientMetaData().describeCommand(output.withStyle(UserInput), taskName);
            output.text(" to get a list of available " + targetName + ".");
        });
        context.appendDocumentationResolution("To learn more about name expansion, visit", "command_line_interface", "sec:name_abbreviation");
    }
}
