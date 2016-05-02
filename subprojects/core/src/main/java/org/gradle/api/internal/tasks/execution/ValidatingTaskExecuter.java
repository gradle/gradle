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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link TaskExecuter} which performs validation before executing the task.
 */
public class ValidatingTaskExecuter implements TaskExecuter {
    private final TaskExecuter executer;

    public ValidatingTaskExecuter(TaskExecuter executer) {
        this.executer = executer;
    }

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        List<String> messages = new ArrayList<String>();
        for (TaskValidator validator : task.getValidators()) {
            validator.validate(task, messages);
        }
        if (!messages.isEmpty()) {
            List<InvalidUserDataException> causes = new ArrayList<InvalidUserDataException>();
            messages = messages.subList(0, Math.min(5, messages.size()));
            for (String message : messages) {
                causes.add(new InvalidUserDataException(message));
            }
            String errorMessage;
            if (messages.size() == 1) {
                errorMessage = String.format("A problem was found with the configuration of %s.", task);
            } else {
                errorMessage = String.format("Some problems were found with the configuration of %s.", task);
            }
            state.executed(new TaskValidationException(errorMessage, causes));
            return;
        }
        executer.execute(task, state, context);
    }
}
