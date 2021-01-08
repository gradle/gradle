/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.tasks.TaskState;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scopes;

@EventScope(Scopes.Build.class)
public interface TaskListenerInternal {
    /**
     * This method is called immediately before a task is executed.
     *
     * @param taskIdentity The identity of the task about to be executed. Never null.
     */
    void beforeExecute(TaskIdentity<?> taskIdentity);

    /**
     * This method is call immediately after a task has been executed. It is always called, regardless of whether the
     * task completed successfully, or failed with an exception.
     *
     * @param taskIdentity The identity of the task which was executed. Never null.
     * @param state The task state. If the task failed with an exception, the exception is available in this
     * state. Never null.
     */
    void afterExecute(TaskIdentity<?>  taskIdentity, TaskState state);

}
