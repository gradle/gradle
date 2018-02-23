/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * A {@link org.gradle.api.internal.tasks.TaskExecuter} which will execute a task once only.
 */
public class ExecuteAtMostOnceTaskExecuter implements TaskExecuter {
    private static final Logger LOGGER = Logging.getLogger(ExecuteAtMostOnceTaskExecuter.class);
    private final TaskExecuter executer;

    public ExecuteAtMostOnceTaskExecuter(TaskExecuter executer) {
        this.executer = executer;
    }

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if (state.getExecuted()) {
            return;
        }
        LOGGER.debug("Starting to execute {}", task);
        try {
            executer.execute(task, state, context);
        } finally {
            LOGGER.debug("Finished executing {}", task);
        }
    }
}
