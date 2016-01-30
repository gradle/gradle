/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.rules;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import java.util.List;

class TaskTypeTaskStateChanges extends SimpleTaskStateChanges {
    private final String taskClass;
    private final TaskExecution previousExecution;
    private final TaskInternal task;

    public TaskTypeTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task) {
        final String taskClass = task.getClass().getName();
        currentExecution.setTaskClass(taskClass);
        this.taskClass = taskClass;
        this.previousExecution = previousExecution;
        this.task = task;
    }

    @Override
    protected void addAllChanges(List<TaskStateChange> changes) {
        if (!taskClass.equals(previousExecution.getTaskClass())) {
            changes.add(new DescriptiveChange("%s has changed type from '%s' to '%s'.",
                    StringUtils.capitalize(task.toString()), previousExecution.getTaskClass(), task.getClass().getName()));
        }
    }
}
