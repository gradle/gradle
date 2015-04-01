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
package org.gradle.profile;

import java.util.Collection;

/**
 * This is a container for task totals across projects grouped by name.
 */
public class TaskTotals extends CompositeOperation<TaskExecution> {
    private final String taskType;

    public TaskTotals(String taskType, Collection<TaskExecution> tasks) {
        super(tasks);
        this.taskType = taskType;
    }

    public String getTaskType() {
        return taskType;
    }

    public boolean addTask(TaskExecution execution) {
        if(execution.getPath().endsWith(taskType)) {
            getOperations().add(execution);
            return true;
        }
        return false;
    }
}
