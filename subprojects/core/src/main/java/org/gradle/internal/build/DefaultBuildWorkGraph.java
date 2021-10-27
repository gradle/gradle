/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.composite.internal.IncludedBuildTaskResource;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultBuildWorkGraph implements BuildWorkGraph {
    private final Object lock = new Object();
    private final Map<String, DefaultExportedTaskNode> nodesByPath = new HashMap<>();
    private final TaskExecutionGraphInternal taskGraph;
    private final ProjectStateRegistry projectStateRegistry;
    private final BuildLifecycleController controller;
    private boolean tasksScheduled;

    public DefaultBuildWorkGraph(TaskExecutionGraphInternal taskGraph, ProjectStateRegistry projectStateRegistry, BuildLifecycleController controller) {
        this.taskGraph = taskGraph;
        this.projectStateRegistry = projectStateRegistry;
        this.controller = controller;
    }

    @Override
    public ExportedTaskNode locateTask(TaskInternal task) {
        DefaultExportedTaskNode node = doLocate(task.getPath());
        node.maybeBindTask(task);
        return node;
    }

    @Override
    public ExportedTaskNode locateTask(String taskPath) {
        return doLocate(taskPath);
    }

    @Override
    public void schedule(Collection<ExportedTaskNode> taskNodes) {
        List<Task> tasks = new ArrayList<>();
        for (ExportedTaskNode taskNode : taskNodes) {
            DefaultExportedTaskNode node = (DefaultExportedTaskNode) taskNode;
            if (nodesByPath.get(node.taskPath) != taskNode) {
                throw new IllegalArgumentException();
            }
            node.whenScheduled();
            tasks.add(node.getTask());
        }
        tasksScheduled = true;
        projectStateRegistry.withMutableStateOfAllProjects(() -> {
            controller.prepareToScheduleTasks();
            controller.populateWorkGraph(taskGraph -> {
                for (Task task : tasks) {
                    taskGraph.addEntryTasks(Collections.singletonList(task));
                }
            });
        });
    }

    @Override
    public void prepareForExecution(boolean alwaysPopulateWorkGraph) {
        controller.finalizeWorkGraph(alwaysPopulateWorkGraph || tasksScheduled);
        updateTasksPriorToExecution();
    }

    @Override
    public ExecutionResult<Void> execute() {
        try {
            if (tasksScheduled) {
                return controller.executeTasks();
            } else {
                return ExecutionResult.succeeded();
            }
        } finally {
            updateTasksAfterExecution();
            tasksScheduled = false;
        }
    }

    private void updateTasksPriorToExecution() {
        for (DefaultExportedTaskNode value : nodesByPath.values()) {
            value.beforeExecution();
        }
    }

    private void updateTasksAfterExecution() {
        for (DefaultExportedTaskNode value : nodesByPath.values()) {
            value.afterExecution();
        }
    }

    private DefaultExportedTaskNode doLocate(String taskPath) {
        return nodesByPath.computeIfAbsent(taskPath, DefaultExportedTaskNode::new);
    }

    private TaskInternal getTask(String taskPath) {
        TaskInternal task = findTaskInWorkGraph(taskPath);
        if (task == null) {
            throw new IllegalStateException("Root build task '" + taskPath + "' was never scheduled for execution.");
        }
        return task;
    }

    private IllegalStateException includedBuildTaskWasNeverScheduled(String taskPath) {
        return new IllegalStateException("Included build task '" + taskPath + "' was never scheduled for execution.");
    }

    private TaskInternal findTaskInWorkGraph(String taskPath) {
        for (Task task : taskGraph.getAllTasks()) {
            if (task.getPath().equals(taskPath)) {
                return (TaskInternal) task;
            }
        }
        return null;
    }

    private enum TaskState {
        Idle, NotScheduled, Scheduled, Finished
    }

    // This should be backed by a TaskNode to determine the actual execution state,
    // rather than trying to reverse engineer the state
    private class DefaultExportedTaskNode implements ExportedTaskNode {
        final String taskPath;
        TaskInternal task;
        TaskState state = TaskState.Idle;

        DefaultExportedTaskNode(String taskPath) {
            this.taskPath = taskPath;
        }

        void maybeBindTask(TaskInternal task) {
            synchronized (lock) {
                if (this.task == null) {
                    this.task = task;
                }
            }
        }

        @Override
        public TaskInternal getTask() {
            synchronized (lock) {
                if (task == null) {
                    task = DefaultBuildWorkGraph.this.getTask(taskPath);
                }
                return task;
            }
        }

        @Override
        public IncludedBuildTaskResource.State getTaskState() {
            synchronized (lock) {
                if (state == TaskState.NotScheduled) {
                    return IncludedBuildTaskResource.State.SUCCESS;
                }
                if (state == TaskState.Idle) {
                    return IncludedBuildTaskResource.State.WAITING;
                }

                getTask();
                if (task.getState().getFailure() != null) {
                    return IncludedBuildTaskResource.State.FAILED;
                } else if (task.getState().getExecuted()) {
                    return IncludedBuildTaskResource.State.SUCCESS;
                } else if (state == TaskState.Finished) {
                    // Here "failed" means "output is not available, so do not run dependents"
                    return IncludedBuildTaskResource.State.FAILED;
                } else {
                    // Scheduled but not completed
                    return IncludedBuildTaskResource.State.WAITING;
                }
            }
        }

        public void whenScheduled() {
            synchronized (lock) {
                state = TaskState.Scheduled;
            }
        }

        public void beforeExecution() {
            synchronized (lock) {
                if (state == TaskState.Idle) {
                    state = TaskState.NotScheduled;
                }
            }
        }

        public void afterExecution() {
            synchronized (lock) {
                if (state == TaskState.Scheduled) {
                    state = TaskState.Finished;
                } else if (state == TaskState.NotScheduled) {
                    state = TaskState.Idle;
                }
            }
        }
    }
}
