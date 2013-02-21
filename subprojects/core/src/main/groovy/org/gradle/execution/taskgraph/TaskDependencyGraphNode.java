/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.Task;

import java.util.HashSet;
import java.util.Set;

public class TaskDependencyGraphNode implements Comparable<TaskDependencyGraphNode> {
    private final Task task;
    private final Set<TaskDependencyGraphNode> successors = new HashSet<TaskDependencyGraphNode>();
    private boolean required = false;

    public TaskDependencyGraphNode(Task task) {
        this.task = task;
    }

    public Task getTask() {
        return task;
    }

    public Set<TaskDependencyGraphNode> getSuccessors() {
        return successors;
    }

    public boolean getRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public void addEdgeTo(TaskDependencyGraphNode toNode) {
        successors.add(toNode);
    }

    public int compareTo(TaskDependencyGraphNode otherNode) {
        return task.compareTo(otherNode.getTask());
    }
}
