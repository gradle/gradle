/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext;
import org.gradle.internal.Factory;

public class LocalTaskInfoExecutor implements WorkInfoExecutor {
    // This currently needs to be lazy, as it uses state that is not available when the graph is created
    private final Factory<? extends TaskExecuter> taskExecuterFactory;

    public LocalTaskInfoExecutor(Factory<? extends TaskExecuter> taskExecuterFactory) {
        this.taskExecuterFactory = taskExecuterFactory;
    }

    @Override
    public boolean execute(WorkInfo work) {
        if (work instanceof LocalTaskInfo) {
            TaskInternal task = ((LocalTaskInfo) work).getTask();
            TaskStateInternal state = task.getState();
            TaskExecutionContext ctx = new DefaultTaskExecutionContext();
            TaskExecuter taskExecuter = taskExecuterFactory.create();
            assert taskExecuter != null;
            taskExecuter.execute(task, state, ctx);
            return true;
        } else {
            return false;
        }
    }
}
