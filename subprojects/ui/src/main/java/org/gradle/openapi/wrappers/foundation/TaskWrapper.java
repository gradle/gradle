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
package org.gradle.openapi.wrappers.foundation;

import org.gradle.foundation.TaskView;
import org.gradle.openapi.external.foundation.ProjectVersion1;
import org.gradle.openapi.external.foundation.TaskVersion1;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of TaskVersion1 meant to help shield external users from internal changes.
 */
public class TaskWrapper implements TaskVersion1 {

    private TaskView taskView;

    public TaskWrapper(TaskView taskView) {
        this.taskView = taskView;
    }

    public String getName() {
        return taskView.getName();
    }

    public String getDescription() {
        return taskView.getDescription();
    }

    public boolean isDefault() {
        return taskView.isDefault();
    }

    public String getFullTaskName() {
        return taskView.getFullTaskName();
    }

    public ProjectVersion1 getProject() {
        return new ProjectWrapper(taskView.getProject());
    }

    /**
     * Converts the list of TaskView objects to TaskVersion1 objects. It just wraps them.
     *
     * @param taskViewList the source tasks
     * @return the tasks wrapped in TaskWrappers.
     */
    public static List<TaskVersion1> convertTasks(List<TaskView> taskViewList) {
        List<TaskVersion1> returnTasks = new ArrayList<TaskVersion1>();
        Iterator<TaskView> taskViewIterator = taskViewList.iterator();
        while (taskViewIterator.hasNext()) {
            TaskView taskView = taskViewIterator.next();
            returnTasks.add(new TaskWrapper(taskView));
        }

        return returnTasks;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TaskWrapper)) {
            return false;
        }

        TaskWrapper otherTaskWrapper = (TaskWrapper) obj;
        return otherTaskWrapper.taskView.equals(taskView);
    }

    @Override
    public int hashCode() {
        return taskView.hashCode();
    }

    @Override
    public String toString() {
        return taskView.toString();
    }
}
