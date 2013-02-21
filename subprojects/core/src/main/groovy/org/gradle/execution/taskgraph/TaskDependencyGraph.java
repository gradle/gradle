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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public class TaskDependencyGraph {

    private final LinkedHashMap<Task, TaskDependencyGraphNode> nodes = new LinkedHashMap<Task, TaskDependencyGraphNode>();

    public Set<Task> getTasks() {
        return nodes.keySet();
    }

    public boolean hasTask(Task task) {
        return getTasks().contains(task);
    }

    public void addEdge(Task fromTask, Task toTask) {
        addEdge(fromTask, toTask, true);
    }

    public void addEdge(Task fromTask, Task toTask, boolean toTaskIsRequired) {
        TaskDependencyGraphNode fromNode = getOrCreateNode(fromTask);
        fromNode.setRequired(true);

        TaskDependencyGraphNode toNode = getOrCreateNode(toTask);
        if (toTaskIsRequired) {
            toNode.setRequired(true);
        }

        fromNode.addEdgeTo(toNode);
    }

    public void addNode(Task task) {
        getOrCreateNode(task).setRequired(true);
    }

    public TaskDependencyGraphNode getNode(Task task) {
        return nodes.get(task);
    }

    private TaskDependencyGraphNode getOrCreateNode(Task task) {
        TaskDependencyGraphNode node = nodes.get(task);
        if (node == null) {
            node = new TaskDependencyGraphNode(task);
            nodes.put(task, node);
        }
        return node;
    }

    public void clear() {
        nodes.clear();
    }
}
