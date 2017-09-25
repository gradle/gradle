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

package org.gradle.execution.taskgraph;

import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionAdapter;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.execution.internal.ExecuteTaskBuildOperationDetails;
import org.gradle.api.execution.internal.ExecuteTaskBuildOperationResult;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskState;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.Factory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationCategory;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationState;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.listener.ClosureBackedMethodInvocationDispatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultTaskGraphExecuter implements TaskGraphExecuter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskGraphExecuter.class);

    private enum TaskGraphState {
        EMPTY, DIRTY, POPULATED
    }

    private final TaskPlanExecutor taskPlanExecutor;
    // This currently needs to be lazy, as it uses state that is not available when the graph is created
    private final Factory<? extends TaskExecuter> taskExecuter;
    private final ListenerBroadcast<TaskExecutionGraphListener> graphListeners;
    private final ListenerBroadcast<TaskExecutionListener> taskListeners;
    private final DefaultTaskExecutionPlan taskExecutionPlan;
    private final BuildOperationExecutor buildOperationExecutor;
    private TaskGraphState taskGraphState = TaskGraphState.EMPTY;

    private final Set<Task> requestedTasks = Sets.newTreeSet();
    private Spec<? super Task> filter = Specs.SATISFIES_ALL;

    public DefaultTaskGraphExecuter(ListenerManager listenerManager, TaskPlanExecutor taskPlanExecutor, Factory<? extends TaskExecuter> taskExecuter, BuildCancellationToken cancellationToken, BuildOperationExecutor buildOperationExecutor, WorkerLeaseService workerLeaseService, ResourceLockCoordinationService coordinationService, GradleInternal gradleInternal) {
        this.taskPlanExecutor = taskPlanExecutor;
        this.taskExecuter = taskExecuter;
        this.buildOperationExecutor = buildOperationExecutor;
        graphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class);
        taskListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class);
        taskExecutionPlan = new DefaultTaskExecutionPlan(cancellationToken, coordinationService, workerLeaseService, gradleInternal);
    }

    public void useFailureHandler(TaskFailureHandler handler) {
        taskExecutionPlan.useFailureHandler(handler);
    }

    public void useFilter(Spec<? super Task> filter) {
        this.filter = (Spec<? super Task>) (filter != null ? filter : Specs.SATISFIES_ALL);
        taskExecutionPlan.useFilter(this.filter);
        taskGraphState = TaskGraphState.DIRTY;
    }

    public void addTasks(Iterable<? extends Task> tasks) {
        assert tasks != null;

        final Timer clock = Time.startTimer();

        Set<Task> taskSet = new LinkedHashSet<Task>();
        for (Task task : tasks) {
            taskSet.add(task);
            requestedTasks.add(task);
        }

        taskExecutionPlan.addToTaskGraph(taskSet);
        taskGraphState = TaskGraphState.DIRTY;

        LOGGER.debug("Timing: Creating the DAG took " + clock.getElapsed());
    }

    @Override
    public void populate() {
        ensurePopulated();
    }

    public void execute() {
        Timer clock = Time.startTimer();
        ensurePopulated();

        graphListeners.getSource().graphPopulated(this);
        try {
            taskPlanExecutor.process(taskExecutionPlan, new EventFiringTaskWorker(taskExecuter.create(), buildOperationExecutor.getCurrentOperation()));
            LOGGER.debug("Timing: Executing the DAG took " + clock.getElapsed());
        } finally {
            taskExecutionPlan.clear();
        }
    }

    public void addTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.add(listener);
    }

    public void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.remove(listener);
    }

    public void whenReady(final Closure closure) {
        graphListeners.add(new ClosureBackedMethodInvocationDispatch("graphPopulated", closure));
    }

    public void whenReady(final Action<TaskExecutionGraph> action) {
        graphListeners.add(new TaskExecutionGraphListener() {
            @Override
            public void graphPopulated(TaskExecutionGraph graph) {
                action.execute(graph);
            }
        });
    }

    public void addTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.add(listener);
    }

    public void removeTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.remove(listener);
    }

    public void beforeTask(final Closure closure) {
        taskListeners.add(new ClosureBackedMethodInvocationDispatch("beforeExecute", closure));
    }

    public void beforeTask(final Action<Task> action) {
        taskListeners.add(new TaskExecutionAdapter() {
            @Override
            public void beforeExecute(Task task) {
                action.execute(task);
            }
        });
    }

    public void afterTask(final Closure closure) {
        taskListeners.add(new ClosureBackedMethodInvocationDispatch("afterExecute", closure));
    }

    public void afterTask(final Action<Task> action) {
        taskListeners.add(new TaskExecutionAdapter() {
            @Override
            public void afterExecute(Task task, TaskState state) {
                action.execute(task);
            }
        });
    }

    public boolean hasTask(Task task) {
        ensurePopulated();
        return taskExecutionPlan.getTasks().contains(task);
    }

    public boolean hasTask(String path) {
        ensurePopulated();
        assert path != null && path.length() > 0;
        for (Task task : taskExecutionPlan.getTasks()) {
            if (task.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    public List<Task> getAllTasks() {
        ensurePopulated();
        return taskExecutionPlan.getTasks();
    }

    private void ensurePopulated() {
        switch (taskGraphState) {
            case EMPTY:
                throw new IllegalStateException(
                    "Task information is not available, as this task execution graph has not been populated.");
            case DIRTY:
                taskExecutionPlan.determineExecutionPlan();
                taskGraphState = TaskGraphState.POPULATED;
                return;
            case POPULATED:
        }
    }

    /**
     * This action will set the start and end times on the internal task state, and will make sure
     * that when a task is started, the public listeners are executed after the internal listeners
     * are executed and when a task is finished, the public listeners are executed before the internal
     * listeners are executed. Basically the internal listeners embrace the public listeners.
     */
    private class EventFiringTaskWorker implements Action<TaskInternal> {
        private final TaskExecuter taskExecuter;
        private final BuildOperationState parentOperation;

        EventFiringTaskWorker(TaskExecuter taskExecuter, BuildOperationState parentOperation) {
            this.taskExecuter = taskExecuter;
            this.parentOperation = parentOperation;
        }

        @Override
        public void execute(final TaskInternal task) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    taskListeners.getSource().beforeExecute(task);

                    TaskStateInternal state = task.getState();
                    TaskExecutionContext ctx = new DefaultTaskExecutionContext();
                    taskExecuter.execute(task, state, ctx);
                    context.setResult(new ExecuteTaskBuildOperationResult(state, ctx));

                    // If this fails, it masks the task failure.
                    // It should addSuppressed() the task failure if there was one.
                    taskListeners.getSource().afterExecute(task, state);

                    context.setStatus(state.getFailure() != null ? "FAILED" : state.getSkipMessage());
                    context.failed(state.getFailure());
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    ExecuteTaskBuildOperationDetails taskOperation = new ExecuteTaskBuildOperationDetails(task);
                    return BuildOperationDescriptor.displayName("Task " + task.getIdentityPath())
                        .name(task.getIdentityPath().toString())
                        .parent(parentOperation)
                        .operationType(BuildOperationCategory.TASK)
                        .details(taskOperation);
                }
            });
        }
    }

    public Set<Task> getRequestedTasks() {
        return requestedTasks;
    }

    public Set<Task> getFilteredTasks() {
        /*
            Note: we currently extract this information from the execution plan because it's
            buried under functions in #filter. This could be detangled/simplified by introducing
            excludeTasks(Iterable<Task>) as an analog to addTasks(Iterable<Task>).

            This is too drastic a change for the stage in the release cycle were exposing this information
            was necessary, therefore the minimal change solution was implemented.
         */
        return taskExecutionPlan.getFilteredTasks();
    }

}
