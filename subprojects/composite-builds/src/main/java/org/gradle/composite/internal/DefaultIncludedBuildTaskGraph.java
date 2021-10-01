/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.ExportedTaskNode;
import org.gradle.internal.buildtree.BuildTreeWorkGraph;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType;
import org.gradle.internal.work.WorkerLeaseService;

import java.io.Closeable;
import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;


public class DefaultIncludedBuildTaskGraph implements BuildTreeWorkGraphController, Closeable {
    private enum State {
        NotPrepared, Preparing, ReadyToRun, Running, Finished
    }

    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildStateRegistry buildRegistry;
    private final WorkerLeaseService workerLeaseService;
    private final ProjectStateRegistry projectStateRegistry;
    private final ManagedExecutor executorService;
    // Lock protects the following state
    private final Object lock = new Object();
    private DefaultBuildTreeWorkGraph current;

    public DefaultIncludedBuildTaskGraph(
        ExecutorFactory executorFactory,
        BuildOperationExecutor buildOperationExecutor,
        BuildStateRegistry buildRegistry,
        ProjectStateRegistry projectStateRegistry,
        WorkerLeaseService workerLeaseService
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildRegistry = buildRegistry;
        this.projectStateRegistry = projectStateRegistry;
        this.executorService = executorFactory.create("included builds");
        this.workerLeaseService = workerLeaseService;
    }

    private DefaultBuildControllers createControllers() {
        return new DefaultBuildControllers(executorService, projectStateRegistry, workerLeaseService);
    }

    @Override
    public <T> T withNewWorkGraph(Function<? super BuildTreeWorkGraph, T> action) {
        DefaultBuildTreeWorkGraph previous;
        DefaultBuildTreeWorkGraph workGraph;
        synchronized (lock) {
            previous = current;
            if (previous != null && previous.state != State.Running && previous.owner != Thread.currentThread()) {
                throw new IllegalStateException("Work graph is already in use.");
            }
            // Else, some other thread is currently running tasks.
            // The later can happen when a task performs dependency resolution without declaring it and the resolution
            // includes a dependency substitution on an included build or a source dependency build
            // Allow this to proceed, but this should become an error at some point
            workGraph = new DefaultBuildTreeWorkGraph();
            current = workGraph;
        }
        try {
            try {
                return action.apply(workGraph);
            } finally {
                workGraph.close();
            }
        } finally {
            synchronized (this) {
                current = previous;
            }
        }
    }

    @Override
    public IncludedBuildTaskResource locateTask(BuildIdentifier targetBuild, TaskInternal task) {
        return withState(workGraph -> {
            BuildState build = buildRegistry.getBuild(targetBuild);
            ExportedTaskNode taskNode = build.getWorkGraph().locateTask(task);
            return new TaskBackedResource(workGraph, build, taskNode);
        });
    }

    @Override
    public IncludedBuildTaskResource locateTask(BuildIdentifier targetBuild, String taskPath) {
        return withState(workGraph -> {
            BuildState build = buildRegistry.getBuild(targetBuild);
            ExportedTaskNode taskNode = build.getWorkGraph().locateTask(taskPath);
            return new TaskBackedResource(workGraph, build, taskNode);
        });
    }

    @Override
    public void close() throws IOException {
        synchronized (lock) {
            if (current != null) {
                throw new IllegalStateException("Work graph was not stopped.");
            }
        }
        CompositeStoppable.stoppable(executorService);
    }

    private <T> T withState(Function<DefaultBuildTreeWorkGraph, T> action) {
        DefaultBuildTreeWorkGraph workGraph;
        synchronized (this) {
            if (current == null) {
                throw new IllegalStateException("No work graph available.");
            }
            workGraph = current;
        }
        workGraph.assertIsOwner();
        return action.apply(workGraph);
    }

    private class DefaultBuildTreeWorkGraphBuilder implements BuildTreeWorkGraph.Builder {
        private final DefaultBuildTreeWorkGraph owner;

        public DefaultBuildTreeWorkGraphBuilder(DefaultBuildTreeWorkGraph owner) {
            this.owner = owner;
        }

        @Override
        public void withWorkGraph(BuildState target, Consumer<? super BuildLifecycleController.WorkGraphBuilder> action) {
            owner.controllers.getBuildController(target).populateWorkGraph(action);
        }
    }

    private class DefaultBuildTreeWorkGraph implements BuildTreeWorkGraph, AutoCloseable {
        private final Thread owner;
        private final BuildControllers controllers;
        private State state = State.NotPrepared;

        public DefaultBuildTreeWorkGraph() {
            owner = Thread.currentThread();
            controllers = createControllers();
        }

        public void queueForExecution(BuildState build, ExportedTaskNode taskNode) {
            assertIsOwner();
            assertCanQueueTask();
            controllers.getBuildController(build).queueForExecution(taskNode);
        }

        @Override
        public void scheduleWork(Consumer<? super Builder> action) {
            assertIsOwner();
            expectInState(State.NotPrepared);
            state = State.Preparing;
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    action.accept(new DefaultBuildTreeWorkGraphBuilder(DefaultBuildTreeWorkGraph.this));
                    controllers.populateWorkGraphs();
                    context.setResult(new CalculateTreeTaskGraphBuildOperationType.Result() {
                    });
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Calculate build tree task graph")
                        .details(new CalculateTreeTaskGraphBuildOperationType.Details() {
                        });
                }
            });
            state = State.ReadyToRun;
        }

        @Override
        public ExecutionResult<Void> runWork() {
            assertIsOwner();
            expectInState(State.ReadyToRun);
            state = State.Running;
            try {
                controllers.startExecution();
                return controllers.awaitCompletion();
            } finally {
                state = State.Finished;
            }
        }

        @Override
        public void close() {
            assertIsOwner();
            controllers.close();
        }

        private void assertCanQueueTask() {
            expectInState(State.Preparing);
        }

        private void expectInState(State expectedState) {
            if (state != expectedState) {
                throw unexpectedState();
            }
        }

        private IllegalStateException unexpectedState() {
            return new IllegalStateException("Work graph is in an unexpected state: " + state);
        }

        private void assertIsOwner() {
            if (Thread.currentThread() != owner) {
                throw new IllegalStateException("Current thread is not the owner of this work graph.");
            }
        }
    }

    private static class TaskBackedResource implements IncludedBuildTaskResource {
        private final DefaultBuildTreeWorkGraph workGraph;
        private final BuildState build;
        private final ExportedTaskNode taskNode;

        public TaskBackedResource(DefaultBuildTreeWorkGraph workGraph, BuildState build, ExportedTaskNode taskNode) {
            this.workGraph = workGraph;
            this.build = build;
            this.taskNode = taskNode;
        }

        @Override
        public void queueForExecution() {
            workGraph.queueForExecution(build, taskNode);
        }

        @Override
        public TaskInternal getTask() {
            return taskNode.getTask();
        }

        @Override
        public State getTaskState() {
            return taskNode.getTaskState();
        }
    }
}
