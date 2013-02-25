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
import org.gradle.api.internal.TaskInternal;

import java.util.LinkedHashMap;
import java.util.Set;

public class TaskDependencyGraph {

    private final LinkedHashMap<Task, TaskInfo> nodes = new LinkedHashMap<Task, TaskInfo>();

    public Set<Task> getTasks() {
        return nodes.keySet();
    }

    public boolean hasTask(Task task) {
        return getTasks().contains(task);
    }

    public void addHardEdge(TaskInfo fromNode, Task toTask) {
        TaskInfo toNode = getOrCreateNode(toTask);
        toNode.setRequired(true);
        fromNode.addHardSuccessor(toNode);
    }

    public void addSoftEdge(TaskInfo fromNode, Task toTask) {
        fromNode.addSoftSuccessor(getOrCreateNode(toTask));
    }

    public TaskInfo addNode(Task task) {
        TaskInfo node = getOrCreateNode(task);
        node.setRequired(true);
        return node;
    }

    public TaskInfo getNode(Task task) {
        return nodes.get(task);
    }

    private TaskInfo getOrCreateNode(Task task) {
        TaskInfo node = nodes.get(task);
        if (node == null) {
            node = new TaskInfo((TaskInternal) task);
            nodes.put(task, node);
        }
        return node;
    }

    public void clear() {
        nodes.clear();
    }
}
