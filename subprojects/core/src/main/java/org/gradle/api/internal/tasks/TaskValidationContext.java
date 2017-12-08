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

package org.gradle.api.internal.tasks;

import com.google.common.collect.Lists;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.TaskValidationException;
import org.gradle.util.DeprecationLogger;

import java.util.List;

public interface TaskValidationContext {
    enum Severity {
        WARNING() {
            @Override
            public boolean report(Task task, List<String> messages, TaskStateInternal state) {
                StringBuilder builder = new StringBuilder();
                builder.append(getMainMessage(task, messages));
                builder.append(" Registering invalid inputs and outputs via TaskInputs and TaskOutputs methods ");
                builder.append(DeprecationLogger.getDeprecationMessage());
                builder.append(".");
                for (String message : messages) {
                    builder.append("\n - ");
                    builder.append(message);
                }
                DeprecationLogger.nagUserWith(builder.toString());
                return true;
            }
        },
        ERROR() {
            @Override
            public boolean report(Task task, List<String> messages, TaskStateInternal state) {
                List<InvalidUserDataException> causes = Lists.newArrayListWithCapacity(messages.size());
                for (String message : messages) {
                    causes.add(new InvalidUserDataException(message));
                }
                String errorMessage = getMainMessage(task, messages);
                state.setOutcome(new TaskValidationException(errorMessage, causes));
                return false;
            }
        };

        private static String getMainMessage(Task task, List<String> messages) {
            if (messages.size() == 1) {
                return String.format("A problem was found with the configuration of %s.", task);
            } else {
                return String.format("Some problems were found with the configuration of %s.", task);
            }
        }

        /**
         * Reports task validation errors. Returns {@code true} if task execution should continue, {@code false} otherwise.
         */
        public abstract boolean report(Task task, List<String> messages, TaskStateInternal state);
    }

    FileResolver getResolver();

    void recordValidationMessage(Severity severity, String message);

    Severity getHighestSeverity();
}
