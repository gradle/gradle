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
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.ExportedTaskNode;
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
import java.util.function.Supplier;


public class DefaultIncludedBuildTaskGraph implements IncludedBuildTaskGraph, Closeable {
    private enum State {
        NotCreated, NotPrepared, QueuingTasks, Populated, ReadyToRun, Running, Finished
    }

    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildStateRegistry buildRegistry;
    private final WorkerLeaseService workerLeaseService;
    private final ProjectStateRegistry projectStateRegistry;
    private final ManagedExecutor executorService;
    private Thread owner;
    private State state = State.NotCreated;
    private IncludedBuildControllers includedBuilds;

    public DefaultIncludedBuildTaskGraph(ExecutorFactory executorFactory,
                                         BuildOperationExecutor buildOperationExecutor,
                                         BuildStateRegistry buildRegistry,
                                         ProjectStateRegistry projectStateRegistry,
                                         WorkerLeaseService workerLeaseService) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildRegistry = buildRegistry;
        this.projectStateRegistry = projectStateRegistry;
        this.executorService = executorFactory.create("included builds");
        this.workerLeaseService = workerLeaseService;
        this.includedBuilds = createControllers();
    }

    private DefaultIncludedBuildControllers createControllers() {
        return new DefaultIncludedBuildControllers(executorService, buildRegistry, projectStateRegistry, workerLeaseService);
    }

    @Override
    public <T> T withNewTaskGraph(Supplier<T> action) {
        Thread currentOwner;
        State currentState;
        IncludedBuildControllers currentControllers;
        synchronized (this) {
            if (state != State.Running) {
                if (owner != null && owner != Thread.currentThread()) {
                    throw new IllegalStateException("This task graph is already in use.");
                }
            }
            // Else, some other thread is currently running tasks.
            // The later can happen when a task performs dependency resolution without declaring it and the resolution
            // includes a dependency substitution on an included build or a source dependency build
            // Allow this to proceed, but this should become an error at some point
            currentOwner = owner;
            currentState = state;
            currentControllers = includedBuilds;
            owner = Thread.currentThread();
            state = State.NotPrepared;
            includedBuilds = createControllers();
        }

        try {
            return action.get();
        } finally {
            includedBuilds.close();
            synchronized (this) {
                owner = currentOwner;
                state = currentState;
                includedBuilds = currentControllers;
            }
        }
    }

    @Override
    public void prepareTaskGraph(Runnable action) {
        withState(() -> {
            expectInState(State.NotPrepared);
            state = State.QueuingTasks;
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    action.run();
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
            expectInState(State.Populated);
            state = State.ReadyToRun;
            return null;
        });
    }

    @Override
    public IncludedBuildTaskResource locateTask(BuildIdentifier targetBuild, TaskInternal task) {
        return withState(() -> {
            assertCanLocateTask();
            IncludedBuildController buildController = includedBuilds.getBuildController(targetBuild);
            return new TaskBackedResource(buildController, buildController.locateTask(task));
        });
    }

    @Override
    public IncludedBuildTaskResource locateTask(BuildIdentifier targetBuild, String taskPath) {
        return withState(() -> {
            assertCanLocateTask();
            IncludedBuildController buildController = includedBuilds.getBuildController(targetBuild);
            return new TaskBackedResource(buildController, buildController.locateTask(taskPath));
        });
    }

    private void queueForExecution(IncludedBuildController buildController, ExportedTaskNode taskNode) {
        withState(() -> {
            assertCanQueueTask();
            buildController.queueForExecution(taskNode);
            return null;
        });
    }

    @Override
    public void populateTaskGraphs() {
        withState(() -> {
            assertCanQueueTask();
            includedBuilds.populateTaskGraphs();
            state = State.Populated;
            return null;
        });
    }

    @Override
    public void startTaskExecution() {
        withState(() -> {
            expectInState(State.ReadyToRun);
            state = State.Running;
            includedBuilds.startTaskExecution();
            return null;
        });
    }

    @Override
    public ExecutionResult<Void> awaitTaskCompletion() {
        return withState(() -> {
            expectInState(State.Running);
            try {
                return includedBuilds.awaitTaskCompletion();
            } finally {
                state = State.Finished;
            }
        });
    }

    @Override
    public void runScheduledTasks() {
        startTaskExecution();
        ExecutionResult<Void> result = awaitTaskCompletion();
        result.rethrow();
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(includedBuilds, executorService);
    }

    private void assertCanLocateTask() {
        if (state == State.NotPrepared) {
            return;
        }
        assertCanQueueTask();
    }

    private void assertCanQueueTask() {
        if (state != State.QueuingTasks && state != State.Populated) {
            throw unexpectedState();
        }
    }

    private void expectInState(State expectedState) {
        if (state != expectedState) {
            throw unexpectedState();
        }
    }

    private IllegalStateException unexpectedState() {
        return new IllegalStateException("Work graph is in an unexpected state: " + state);
    }

    private <T> T withState(Supplier<T> action) {
        Thread currentOwner;
        synchronized (this) {
            currentOwner = owner;
            if (owner == null) {
                owner = Thread.currentThread();
            } else if (owner != Thread.currentThread()) {
                // Currently, only a single thread should work with the task graph at a time
                throw new IllegalStateException("This task graph is already in use.");
            }
        }
        try {
            return action.get();
        } finally {
            synchronized (this) {
                if (owner != Thread.currentThread()) {
                    throw new IllegalStateException("This task graph is in use by another thread.");
                }
                owner = currentOwner;
            }
        }
    }

    private class TaskBackedResource implements IncludedBuildTaskResource {
        private final IncludedBuildController buildController;
        private final ExportedTaskNode taskNode;

        public TaskBackedResource(IncludedBuildController buildController, ExportedTaskNode taskNode) {
            this.buildController = buildController;
            this.taskNode = taskNode;
        }

        @Override
        public void queueForExecution() {
            DefaultIncludedBuildTaskGraph.this.queueForExecution(buildController, taskNode);
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
