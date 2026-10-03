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
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.specs.Spec;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.plan.BuildWorkPlan;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.execution.plan.TaskNode;
import org.gradle.execution.plan.TaskNodeFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DefaultBuildWorkGraphController implements BuildWorkGraphController {
    private final TaskNodeFactory taskNodeFactory;
    private final BuildLifecycleController controller;
    private final BuildIdentifier buildIdentifier;
    private final WorkerLeaseService workerLeaseService;
    private final Object lock = new Object();
    private Thread currentOwner;
    private final Set<DefaultBuildWorkGraph> pendingGraphs = new HashSet<>();
    private DefaultBuildWorkGraph currentlyRunning;

    public DefaultBuildWorkGraphController(TaskNodeFactory taskNodeFactory, BuildLifecycleController controller, BuildState buildState, WorkerLeaseService workerLeaseService) {
        this.taskNodeFactory = taskNodeFactory;
        this.controller = controller;
        this.buildIdentifier = buildState.getBuildIdentifier();
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public void resetState() {
        synchronized (lock) {
            if (currentOwner != null) {
                throw new IllegalStateException("Cannot reset work graph state as another thread is currently using the work graph.");
            }
        }
        taskNodeFactory.resetState();
    }

    @Override
    public TaskNode locateTaskNode(TaskInternal task) {
        return taskNodeFactory.getOrCreateNode(task);
    }

    @Override
    public BuildWorkGraph newWorkGraph() {
        synchronized (lock) {
            while (currentOwner != null && currentOwner != Thread.currentThread()) {
                workerLeaseService.blocking(() -> {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                });
            }
            currentOwner = Thread.currentThread();
            DefaultBuildWorkGraph workGraph = new DefaultBuildWorkGraph();
            pendingGraphs.add(workGraph);
            return workGraph;
        }
    }


    private class DefaultBuildWorkGraph implements BuildWorkGraph {
        private final Thread owner;
        BuildWorkPlan plan;

        public DefaultBuildWorkGraph() {
            this.owner = Thread.currentThread();
        }

        @Override
        public void stop() {
            if (plan != null) {
                plan.stop();
            }
            synchronized (lock) {
                assert currentOwner == Thread.currentThread();
                pendingGraphs.remove(this);
                if (pendingGraphs.isEmpty()) {
                    currentOwner = null;
                    lock.notifyAll();
                }
            }
        }

        @Override
        public boolean schedule(Collection<Node> allNodes) {
            assertIsOwner();
            List<Node> entryNodes = new ArrayList<>();
            for (Node node : allNodes) {
                if (!node.isRequired()) {
                    // Not already in task graph
                    entryNodes.add(node);
                }
            }
            if (entryNodes.isEmpty()) {
                return false;
            }
            controller.getGradle().getOwner().getProjects().withMutableStateOfAllProjects(() -> {
                createPlan();
                controller.prepareToScheduleTasks();
                controller.populateWorkGraph(plan, workGraph -> workGraph.addEntryNodes(entryNodes));
            });
            return true;
        }

        @Override
        public void populateWorkGraph(Consumer<? super BuildLifecycleController.WorkGraphBuilder> action) {
            BuildWorkPlan ownedPlan = getOwnedPlan();
            controller.prepareToScheduleTasks();
            controller.populateWorkGraph(ownedPlan, action);
        }

        @Override
        public void addFilter(Spec<Task> filter) {
            getOwnedPlan().addFilter(filter);
        }

        @Override
        public void addFinalization(BiConsumer<EntryTaskSelector.Context, QueryableExecutionPlan> finalization) {
            getOwnedPlan().addFinalization(finalization);
        }

        private BuildWorkPlan getOwnedPlan() {
            assertIsOwner();
            createPlan();
            return plan;
        }

        private void createPlan() {
            if (plan == null) {
                plan = controller.newWorkGraph();
                plan.onComplete(TaskNode::fireCompleted);
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
            synchronized (lock) {
                if (currentlyRunning != null) {
                    throw new IllegalStateException("Build '" + buildIdentifier + "' is currently already running work.");
                }
                currentlyRunning = this;
            }
            try {
                if (plan != null) {
                    return controller.executeTasks(plan);
                } else {
                    return ExecutionResult.succeeded();
                }
            } finally {
                synchronized (lock) {
                    currentlyRunning = null;
                }
            }
        }

        private void assertIsOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Current thread is not the owner of this work graph.");
            }
        }
    }

}
