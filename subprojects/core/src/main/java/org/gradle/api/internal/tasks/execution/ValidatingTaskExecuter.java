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

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DefaultTaskValidationContext;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuterResult;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.tasks.TaskValidationException;

import java.util.List;

/**
 * A {@link TaskExecuter} which performs validation before executing the task.
 */
public class ValidatingTaskExecuter implements TaskExecuter {
    private final TaskExecuter executer;

    public ValidatingTaskExecuter(TaskExecuter executer) {
        this.executer = executer;
    }

    @Override
    public TaskExecuterResult execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        List<String> messages = Lists.newArrayList();
        FileResolver resolver = ((ProjectInternal) task.getProject()).getFileResolver();
        final TaskValidationContext validationContext = new DefaultTaskValidationContext(resolver, messages);

        context.getTaskProperties().validate(validationContext);

        if (!messages.isEmpty()) {
            List<String> firstMessages = messages.subList(0, Math.min(5, messages.size()));
            report(task, firstMessages, state);
            return TaskExecuterResult.WITHOUT_OUTPUTS;
        }

        return executer.execute(task, state, context);
    }

    private static void report(Task task, List<String> messages, TaskStateInternal state) {
        List<InvalidUserDataException> causes = Lists.newArrayListWithCapacity(messages.size());
        for (String message : messages) {
            causes.add(new InvalidUserDataException(message));
        }
        String errorMessage = getMainMessage(task, messages);
        state.setOutcome(new TaskValidationException(errorMessage, causes));
    }

    private static String getMainMessage(Task task, List<String> messages) {
        if (messages.size() == 1) {
            return String.format("A problem was found with the configuration of %s.", task);
        } else {
            return String.format("Some problems were found with the configuration of %s.", task);
        }
    }
}
