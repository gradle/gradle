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
import org.gradle.execution.plan.BuildWorkPlan;
import org.gradle.execution.plan.TaskNode;
import org.gradle.execution.plan.TaskNodeFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DefaultBuildWorkGraphController implements BuildWorkGraphController {
    private final TaskNodeFactory taskNodeFactory;
    private final ProjectStateRegistry projectStateRegistry;
    private final BuildLifecycleController controller;
    private final Object lock = new Object();
    private final Map<String, DefaultExportedTaskNode> nodesByPath = new HashMap<>();
    private DefaultBuildWorkGraph current;

    public DefaultBuildWorkGraphController(TaskNodeFactory taskNodeFactory, ProjectStateRegistry projectStateRegistry, BuildLifecycleController controller) {
        this.taskNodeFactory = taskNodeFactory;
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
    public BuildWorkGraph newWorkGraph() {
        synchronized (lock) {
            if (current != null) {
                throw new IllegalStateException("This build's work graph is currently in use by another thread.");
            }
            current = new DefaultBuildWorkGraph();
            return current;
        }
    }

    private DefaultExportedTaskNode doLocate(String taskPath) {
        return nodesByPath.computeIfAbsent(taskPath, DefaultExportedTaskNode::new);
    }

    @Nullable
    private TaskInternal findTaskNode(String taskPath) {
        for (Task task : taskNodeFactory.getTasks()) {
            if (task.getPath().equals(taskPath)) {
                return (TaskInternal) task;
            }
        }
        return null;
    }

    private class DefaultBuildWorkGraph implements BuildWorkGraph {
        private final Thread owner;
        BuildWorkPlan plan;

        public DefaultBuildWorkGraph() {
            this.owner = Thread.currentThread();
        }

        @Override
        public boolean schedule(Collection<ExportedTaskNode> taskNodes) {
            assertIsOwner();
            List<Task> tasks = new ArrayList<>();
            for (ExportedTaskNode taskNode : taskNodes) {
                DefaultExportedTaskNode node = (DefaultExportedTaskNode) taskNode;
                if (nodesByPath.get(node.taskPath) != taskNode) {
                    throw new IllegalArgumentException();
                }
                if (node.shouldSchedule()) {
                    // Not already in task graph
                    tasks.add(node.getTask());
                }
            }
            if (tasks.isEmpty()) {
                return false;
            }
            projectStateRegistry.withMutableStateOfAllProjects(() -> {
                createPlan();
                controller.populateWorkGraph(plan, taskGraph -> taskGraph.addEntryTasks(tasks));
            });
            return true;
        }

        @Override
        public void populateWorkGraph(Consumer<? super BuildLifecycleController.WorkGraphBuilder> action) {
            assertIsOwner();
            createPlan();
            controller.populateWorkGraph(plan, action);
        }

        private void createPlan() {
            if (plan == null) {
                controller.prepareToScheduleTasks();
                plan = controller.newWorkGraph();
            }
        }

        @Override
        public void finalizeGraph() {
            assertIsOwner();
            if (plan != null) {
                controller.finalizeWorkGraph(plan);
            }
        }

        @Override
        public ExecutionResult<Void> runWork() {
            try {
                if (plan != null) {
                    return controller.executeTasks(plan);
                } else {
                    return ExecutionResult.succeeded();
                }
            } finally {
                synchronized (lock) {
                    current = null;
                }
            }
        }

        private void assertIsOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Current thread is not the owner of this work graph.");
            }
        }
    }

    private class DefaultExportedTaskNode implements ExportedTaskNode {
        final String taskPath;
        TaskNode taskNode;

        DefaultExportedTaskNode(String taskPath) {
            this.taskPath = taskPath;
        }

        void maybeBindTask(TaskInternal task) {
            synchronized (lock) {
                if (taskNode == null) {
                    taskNode = taskNodeFactory.getOrCreateNode(task);
                }
            }
        }

        @Override
        public TaskInternal getTask() {
            synchronized (lock) {
                if (taskNode == null) {
                    TaskInternal task = findTaskNode(taskPath);
                    if (task == null) {
                        throw new IllegalStateException("Task '" + taskPath + "' was never scheduled for execution.");
                    }
                    taskNode = taskNodeFactory.getOrCreateNode(task);
                }
                return taskNode.getTask();
            }
        }

        @Override
        public IncludedBuildTaskResource.State getTaskState() {
            synchronized (lock) {
                if (taskNode == null) {
                    TaskInternal task = findTaskNode(taskPath);
                    if (task == null) {
                        // Assume not scheduled yet
                        return IncludedBuildTaskResource.State.Waiting;
                    }
                    taskNode = taskNodeFactory.getOrCreateNode(task);
                }
                if (taskNode.isExecuted() && taskNode.isSuccessful()) {
                    return IncludedBuildTaskResource.State.Success;
                } else if (taskNode.isComplete() && taskNode.isInKnownState()) {
                    // Here "failed" means "output is not available, so do not run dependents"
                    return IncludedBuildTaskResource.State.Failed;
                } else {
                    // Scheduled but not completed
                    return IncludedBuildTaskResource.State.Waiting;
                }
            }
        }

        public boolean shouldSchedule() {
            synchronized (lock) {
                return taskNode == null || !taskNode.isRequired();
            }
        }
    }
}
