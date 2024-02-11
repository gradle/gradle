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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.specs.Spec;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.plan.PlanExecutor;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.ExportedTaskNode;
import org.gradle.internal.buildtree.BuildTreeWorkGraph;
import org.gradle.internal.buildtree.BuildTreeWorkGraphPreparer;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.taskgraph.CalculateTreeTaskGraphBuildOperationType;
import org.gradle.internal.work.WorkerLeaseService;

import javax.inject.Inject;
import java.io.Closeable;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;


public class DefaultIncludedBuildTaskGraph implements BuildTreeWorkGraphController, Closeable {
    private enum State {
        NotPrepared, Preparing, ReadyToRun, Running, Finished
    }

    private static final int MONITORING_POLL_TIME = 30;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildStateRegistry buildRegistry;
    private final WorkerLeaseService workerLeaseService;
    private final PlanExecutor planExecutor;
    private final BuildTreeWorkGraphPreparer workGraphPreparer;
    private final int monitoringPollTime;
    private final TimeUnit monitoringPollTimeUnit;
    private final ManagedExecutor executorService;
    private final ThreadLocal<DefaultBuildTreeWorkGraph> current = new ThreadLocal<>();

    @Inject
    public DefaultIncludedBuildTaskGraph(
        ExecutorFactory executorFactory,
        BuildOperationExecutor buildOperationExecutor,
        BuildStateRegistry buildRegistry,
        WorkerLeaseService workerLeaseService,
        PlanExecutor planExecutor,
        BuildTreeWorkGraphPreparer workGraphPreparer
    ) {
        this(executorFactory, buildOperationExecutor, buildRegistry, workerLeaseService, planExecutor, workGraphPreparer, MONITORING_POLL_TIME, TimeUnit.SECONDS);
    }

    @VisibleForTesting
    DefaultIncludedBuildTaskGraph(
        ExecutorFactory executorFactory,
        BuildOperationExecutor buildOperationExecutor,
        BuildStateRegistry buildRegistry,
        WorkerLeaseService workerLeaseService,
        PlanExecutor planExecutor,
        BuildTreeWorkGraphPreparer workGraphPreparer,
        int monitoringPollTime,
        TimeUnit monitoringPollTimeUnit
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildRegistry = buildRegistry;
        this.executorService = executorFactory.create("included builds");
        this.workerLeaseService = workerLeaseService;
        this.planExecutor = planExecutor;
        this.workGraphPreparer = workGraphPreparer;
        this.monitoringPollTime = monitoringPollTime;
        this.monitoringPollTimeUnit = monitoringPollTimeUnit;
    }

    private DefaultBuildControllers createControllers() {
        return new DefaultBuildControllers(executorService, workerLeaseService, planExecutor, monitoringPollTime, monitoringPollTimeUnit);
    }

    @Override
    public <T> T withNewWorkGraph(Function<? super BuildTreeWorkGraph, T> action) {
        DefaultBuildTreeWorkGraph previous = current.get();
        DefaultBuildTreeWorkGraph workGraph = new DefaultBuildTreeWorkGraph();
        current.set(workGraph);
        try {
            try {
                return action.apply(workGraph);
            } finally {
                workGraph.close();
            }
        } finally {
            current.set(previous);
        }
    }

    @Override
    public IncludedBuildTaskResource locateTask(TaskIdentifier taskIdentifier) {
        return withState(workGraph -> {
            BuildState build = buildRegistry.getBuild(taskIdentifier.getBuildIdentifier());
            ExportedTaskNode taskNode = build.getWorkGraph().locateTask(taskIdentifier);
            return new TaskBackedResource(workGraph, build, taskNode);
        });
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(executorService);
    }

    private <T> T withState(Function<DefaultBuildTreeWorkGraph, T> action) {
        DefaultBuildTreeWorkGraph workGraph = current.get();
        if (workGraph == null) {
            throw new IllegalStateException("No work graph available for this thread.");
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
            buildControllerOf(target).populateWorkGraph(action);
        }

        @Override
        public void addFilter(BuildState target, Spec<Task> filter) {
            buildControllerOf(target).addFilter(filter);
        }

        @Override
        public void addFinalization(BuildState target, BiConsumer<EntryTaskSelector.Context, QueryableExecutionPlan> finalization) {
            buildControllerOf(target).addFinalization(finalization);
        }

        @Override
        public void scheduleTasks(Collection<TaskIdentifier.TaskBasedTaskIdentifier> tasksToBuild) {
            for (TaskIdentifier.TaskBasedTaskIdentifier identifier : tasksToBuild) {
                // This check should live lower down, and should have some kind of synchronization around it, as other threads may be
                // running tasks at the same time
                if (identifier.getTask().getState().getExecuted()) {
                    continue;
                }
                locateTask(identifier).queueForExecution();
            }
        }

        private BuildController buildControllerOf(BuildState target) {
            return owner.controllers.getBuildController(target);
        }
    }

    private class DefaultBuildTreeWorkGraph implements BuildTreeWorkGraph, BuildTreeWorkGraph.FinalizedGraph, AutoCloseable {
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
        public FinalizedGraph scheduleWork(Consumer<? super Builder> action) {
            assertIsOwner();
            expectInState(State.NotPrepared);
            state = State.Preparing;
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    DefaultBuildTreeWorkGraphBuilder graphBuilder = new DefaultBuildTreeWorkGraphBuilder(DefaultBuildTreeWorkGraph.this);
                    workGraphPreparer.prepareToScheduleTasks(graphBuilder);
                    action.accept(graphBuilder);
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
            return this;
        }

        @Override
        public ExecutionResult<Void> runWork() {
            assertIsOwner();
            expectInState(State.ReadyToRun);
            state = State.Running;
            try {
                return controllers.execute();
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
        public void onComplete(Runnable action) {
            taskNode.onComplete(action);
        }

        @Override
        public TaskInternal getTask() {
            return taskNode.getTask();
        }

        @Override
        public State getTaskState() {
            return taskNode.getTaskState();
        }

        @Override
        public String healthDiagnostics() {
            return taskNode.healthDiagnostics();
        }
    }
}
