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

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

/**
 * A {@link org.gradle.api.internal.tasks.TaskExecuter} which skips tasks that have no actions.
 */
public class SkipTaskWithNoActionsExecuter implements TaskExecuter {
    private static final Logger LOGGER = Logging.getLogger(SkipTaskWithNoActionsExecuter.class);
    private final TaskExecuter executer;

    public SkipTaskWithNoActionsExecuter(TaskExecuter executer) {
        this.executer = executer;
    }

    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        if (task.getActions().isEmpty()) {
            LOGGER.info("Skipping {} as it has no actions.", task);
            boolean upToDate = true;
            for (Task dependency : task.getTaskDependencies().getDependencies(task)) {
                if (!dependency.getState().getSkipped()) {
                    upToDate = false;
                    break;
                }
            }
            if (upToDate) {
                state.upToDate();
            }
            return;
        }
        executer.execute(task, state, context);
    }
}
