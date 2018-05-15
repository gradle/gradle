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

package org.gradle.execution.workgraph;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Task;
import org.gradle.internal.scheduler.Graph;

public class WorkGraph {
    private final Graph graph;
    private final ImmutableMap<Task, TaskNode> taskNodes;
    private final ImmutableSet<TaskNode> requestedNodes;
    private final ImmutableSet<Task> filteredTasks;

    public WorkGraph(Graph graph, ImmutableMap<Task, TaskNode> taskNodes, ImmutableSet<TaskNode> requestedNodes, ImmutableSet<Task> filteredTasks) {
        this.graph = graph;
        this.taskNodes = taskNodes;
        this.requestedNodes = requestedNodes;
        this.filteredTasks = filteredTasks;
    }

    public Graph getGraph() {
        return graph;
    }

    public ImmutableMap<Task, TaskNode> getTaskNodes() {
        return taskNodes;
    }

    public ImmutableSet<TaskNode> getRequestedNodes() {
        return requestedNodes;
    }

    public ImmutableSet<Task> getFilteredTasks() {
        return filteredTasks;
    }
}
