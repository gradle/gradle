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
package org.gradle.api.tasks.diagnostics;

import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.TreeMultimap;
import org.gradle.api.Task;
import org.gradle.api.internal.DirectedGraph;
import org.gradle.api.internal.GraphAggregator;
import org.gradle.util.GUtil;

import java.util.*;

public class TaskReportModel {
    private final SetMultimap<String, TaskDetails> groups = TreeMultimap.create(GUtil.emptyLast(GUtil.caseInsensitive()), Ordering.natural());

    public void calculate(final Collection<? extends Task> tasks) {
        Set<Task> topLevelTasks = new LinkedHashSet<Task>();
        for (final Task task : tasks) {
            if (GUtil.isTrue(task.getTaskGroup())) {
                topLevelTasks.add(task);
            }
        }
        GraphAggregator<Task> aggregator = new GraphAggregator<Task>(new DirectedGraph<Task, Object>() {
            @Override
            public void getNodeValues(Task node, Collection<Object> values, Collection<Task> connectedNodes) {
                for (Task dep : node.getTaskDependencies().getDependencies(node)) {
                    if (tasks.contains(dep)) {
                        connectedNodes.add(dep);
                    }
                }
            }
        });

        GraphAggregator.Result<Task> result = aggregator.group(topLevelTasks, tasks);
        for (Task task : result.getTopLevelNodes()) {
            Set<Task> nodesForThisTask = new TreeSet<Task>(result.getNodes(task));
            Set<TaskDetails> children = new LinkedHashSet<TaskDetails>();
            Set<String> dependencies = new TreeSet<String>();
            for (Task node : nodesForThisTask) {
                if (node != task) {
                    children.add(new TaskDetailsImpl(node, Collections.<TaskDetails>emptySet(),
                            Collections.<String>emptySet()));
                }
                for (Task dep : node.getTaskDependencies().getDependencies(node)) {
                    if (topLevelTasks.contains(dep) || !tasks.contains(dep)) {
                        dependencies.add(dep.getPath());
                    }
                }
            }

            String taskGroup = topLevelTasks.contains(task) ? task.getTaskGroup() : "";
            groups.put(taskGroup, new TaskDetailsImpl(task, children, dependencies));
        }
    }

    public Set<String> getGroups() {
        return groups.keySet();
    }

    public Set<TaskDetails> getTasksForGroup(String group) {
        if (!groups.containsKey(group)) {
            throw new IllegalArgumentException(String.format("Unknown group '%s'", group));
        }
        return groups.get(group);
    }

    private static class TaskDetailsImpl implements TaskDetails {
        private final Task task;
        private final Set<TaskDetails> children;
        private final Set<String> dependencies;

        public TaskDetailsImpl(Task task, Set<TaskDetails> children, Set<String> dependencies) {
            this.task = task;
            this.children = children;
            this.dependencies = dependencies;
        }

        @Override
        public String getDescription() {
            return task.getDescription();
        }

        @Override
        public String getPath() {
            return task.getPath();
        }

        @Override
        public String toString() {
            return task.toString();
        }

        public Task getTask() {
            return task;
        }

        @Override
        public Set<String> getDependencies() {
            return dependencies;
        }

        @Override
        public Set<TaskDetails> getChildren() {
            return children;
        }

        @Override
        public int compareTo(TaskDetails o) {
            return getPath().compareTo(o.getPath());
        }
    }
}
