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

import com.google.common.collect.Sets;
import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.TaskContainerInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskDependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@NonNullApi
public class WorkGraph {

    private final Set<TaskInfo> entryTasks = new LinkedHashSet<TaskInfo>();
    private final TaskInfoFactory nodeFactory;
    private Spec<? super Task> filter = Specs.satisfyAll();
    private final Set<Task> filteredTasks = Sets.newIdentityHashSet();
    private final Set<TaskInfo> tasksInUnknownState = new LinkedHashSet<TaskInfo>();

    public WorkGraph(TaskFailureCollector failureCollector) {
        nodeFactory = new TaskInfoFactory(failureCollector);
    }

    public void addToTaskGraph(Collection<? extends Task> tasks) {
        List<TaskInfo> queue = new ArrayList<TaskInfo>();

        addTaskInfosToQueue(tasks, queue);
        resolveDependencies(queue);
        resolveTasksInUnknownState();
    }

    private void addTaskInfosToQueue(Collection<? extends Task> tasks, List<TaskInfo> queue) {
        List<Task> sortedTasks = new ArrayList<Task>(tasks);
        Collections.sort(sortedTasks);
        for (Task task : sortedTasks) {
            TaskInfo node = nodeFactory.createNode(task);
            if (node.isMustNotRun()) {
                requireWithDependencies(node);
            } else if (filter.isSatisfiedBy(task)) {
                node.require();
            }
            entryTasks.add(node);
            queue.add(node);
        }
    }

    private void requireWithDependencies(TaskInfo taskInfo) {
        if (taskInfo.isMustNotRun() && filter.isSatisfiedBy(taskInfo.getTask())) {
            taskInfo.require();
            for (TaskInfo dependency : taskInfo.getDependencySuccessors()) {
                requireWithDependencies(dependency);
            }
        }
    }

    private void resolveDependencies(List<TaskInfo> queue) {
        Set<TaskInfo> visiting = new HashSet<TaskInfo>();
        CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext();

        while (!queue.isEmpty()) {
            TaskInfo node = queue.get(0);
            if (node.getDependenciesProcessed()) {
                // Have already visited this task - skip it
                queue.remove(0);
                continue;
            }

            TaskInternal task = node.getTask();
            boolean filtered = !filter.isSatisfiedBy(task);
            if (filtered) {
                // Task is not required - skip it
                queue.remove(0);
                node.dependenciesProcessed();
                node.doNotRequire();
                filteredTasks.add(task);
                continue;
            }

            if (visiting.add(node)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue

                // Make sure it has been configured
                ((TaskContainerInternal) task.getProject().getTasks()).prepareForExecution(task);
                context.setTask(task);

                addNodeDependencies(queue, visiting, context, node, task.getTaskDependencies());
                addNodeFinalizers(queue, visiting, context, node, task.getFinalizedBy());

                addMustSuccessors(context, node, task.getMustRunAfter());
                addShouldSuccessors(context, node, task.getShouldRunAfter());

                if (node.isRequired()) {
                    requireSuccessors(node);
                } else {
                    tasksInUnknownState.add(node);
                }
            } else {
                // Have visited this task's dependencies - add it to the graph
                queue.remove(0);
                visiting.remove(node);
                node.dependenciesProcessed();
            }
        }
    }

    private void addNodeFinalizers(List<TaskInfo> queue, Set<TaskInfo> visiting, CachingTaskDependencyResolveContext context, TaskInfo node, TaskDependency finalizedBy) {
        for (Task finalizerTask : context.getDependencies(finalizedBy)) {
            TaskInfo targetNode = nodeFactory.createNode(finalizerTask);
            addFinalizerNode(node, targetNode);
            if (!visiting.contains(targetNode)) {
                queue.add(0, targetNode);
            }
        }
    }

    private void addFinalizerNode(TaskInfo node, TaskInfo finalizerNode) {
        if (filter.isSatisfiedBy(finalizerNode.getTask())) {
            node.addFinalizer(finalizerNode);
            if (!finalizerNode.isInKnownState()) {
                finalizerNode.mustNotRun();
            }
            finalizerNode.addMustSuccessor(node);
        }
    }

    private void addNodeDependencies(List<TaskInfo> queue, Set<TaskInfo> visiting, CachingTaskDependencyResolveContext context, TaskInfo node, TaskDependency taskDependencies) {
        Set<? extends Task> dependsOnTasks = context.getDependencies(taskDependencies);
        for (Task dependsOnTask : dependsOnTasks) {
            TaskInfo targetNode = nodeFactory.createNode(dependsOnTask);
            node.addDependencySuccessor(targetNode);
            if (!visiting.contains(targetNode)) {
                queue.add(0, targetNode);
            }
        }
    }

    private void addMustSuccessors(CachingTaskDependencyResolveContext context, TaskInfo node, TaskDependency mustRunAfterDependency) {
        for (Task mustRunAfter : context.getDependencies(mustRunAfterDependency)) {
            TaskInfo targetNode = nodeFactory.createNode(mustRunAfter);
            node.addMustSuccessor(targetNode);
        }
    }

    private void addShouldSuccessors(CachingTaskDependencyResolveContext context, TaskInfo node, TaskDependency shouldRunAfterDependencies) {
        for (Task shouldRunAfter : context.getDependencies(shouldRunAfterDependencies)) {
            TaskInfo targetNode = nodeFactory.createNode(shouldRunAfter);
            node.addShouldSuccessor(targetNode);
        }
    }

    private void requireSuccessors(TaskInfo node) {
        for (TaskInfo successor : node.getDependencySuccessors()) {
            if (filter.isSatisfiedBy(successor.getTask())) {
                successor.require();
            }
        }
    }

    private void resolveTasksInUnknownState() {
        List<TaskInfo> queue = new ArrayList<TaskInfo>(tasksInUnknownState);
        Set<TaskInfo> visiting = new HashSet<TaskInfo>();

        while (!queue.isEmpty()) {
            TaskInfo task = queue.get(0);
            if (task.isInKnownState()) {
                queue.remove(0);
                continue;
            }

            if (visiting.add(task)) {
                for (TaskInfo hardPredecessor : task.getDependencyPredecessors()) {
                    if (!visiting.contains(hardPredecessor)) {
                        queue.add(0, hardPredecessor);
                    }
                }
            } else {
                queue.remove(0);
                visiting.remove(task);
                task.mustNotRun();
                for (TaskInfo predecessor : task.getDependencyPredecessors()) {
                    assert predecessor.isRequired() || predecessor.isMustNotRun();
                    if (predecessor.isRequired()) {
                        task.require();
                        break;
                    }
                }
            }
        }
    }

    public void useFilter(Spec<? super Task> filter) {
        this.filter = filter;
    }

    public Iterable<TaskInfo> getEntryTasks() {
        return entryTasks;
    }

    public void clear() {
        nodeFactory.clear();
        entryTasks.clear();
    }

    public Set<Task> getFilteredTasks() {
        return filteredTasks;
    }
}
