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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionAdapter;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.execution.internal.InternalTaskExecutionListener;
import org.gradle.api.execution.internal.TaskOperationDescriptor;
import org.gradle.api.execution.internal.TaskOperationInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskState;
import org.gradle.execution.TaskFailureHandler;
import org.gradle.execution.TaskGraphExecuter;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.Factory;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.progress.OperationResult;
import org.gradle.internal.progress.OperationStartEvent;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;
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
    private final InternalTaskExecutionListener internalTaskListener;
    private final DefaultTaskExecutionPlan taskExecutionPlan;
    private final BuildOperationExecutor buildOperationExecutor;
    private TaskGraphState taskGraphState = TaskGraphState.EMPTY;

    public DefaultTaskGraphExecuter(ListenerManager listenerManager, TaskPlanExecutor taskPlanExecutor, Factory<? extends TaskExecuter> taskExecuter, BuildCancellationToken cancellationToken, BuildOperationExecutor buildOperationExecutor) {
        this.taskPlanExecutor = taskPlanExecutor;
        this.taskExecuter = taskExecuter;
        this.buildOperationExecutor = buildOperationExecutor;
        graphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class);
        taskListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class);
        internalTaskListener = listenerManager.getBroadcaster(InternalTaskExecutionListener.class);
        taskExecutionPlan = new DefaultTaskExecutionPlan(cancellationToken);
    }

    public void useFailureHandler(TaskFailureHandler handler) {
        taskExecutionPlan.useFailureHandler(handler);
    }

    public void useFilter(Spec<? super Task> filter) {
        taskExecutionPlan.useFilter(filter);
        taskGraphState = TaskGraphState.DIRTY;
    }

    public void addTasks(Iterable<? extends Task> tasks) {
        assert tasks != null;

        final Timer clock = Timers.startTimer();

        Set<Task> taskSet = new LinkedHashSet<Task>();
        for (Task task : tasks) {
            taskSet.add(task);
        }
        taskExecutionPlan.addToTaskGraph(taskSet);
        taskGraphState = TaskGraphState.DIRTY;

        LOGGER.debug("Timing: Creating the DAG took " + clock.getElapsed());
    }

    public void execute() {
        Timer clock = Timers.startTimer();
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
        private final BuildOperationExecutor.Operation parentOperation;

        EventFiringTaskWorker(TaskExecuter taskExecuter, BuildOperationExecutor.Operation parentOperation) {
            this.taskExecuter = taskExecuter;
            this.parentOperation = parentOperation;
        }

        @Override
        public void execute(final TaskInternal task) {
            TaskOperationDescriptor taskOperation = new TaskOperationDescriptor(task);
            BuildOperationDetails buildOperationDetails = BuildOperationDetails.displayName("Task " + task.getIdentityPath()).name(task.getIdentityPath().toString()).parent(parentOperation).operationDescriptor(taskOperation).build();
            buildOperationExecutor.run(buildOperationDetails, new Action<BuildOperationContext>() {
                @Override
                public void execute(BuildOperationContext buildOperationContext) {
                    // These events are used by build scans
                    TaskOperationInternal legacyOperation = new TaskOperationInternal(task, buildOperationExecutor.getCurrentOperation().getId());
                    internalTaskListener.beforeExecute(legacyOperation, new OperationStartEvent(0));
                    TaskStateInternal state = task.getState();
                    taskListeners.getSource().beforeExecute(task);
                    taskExecuter.execute(task, state, new DefaultTaskExecutionContext());
                    taskListeners.getSource().afterExecute(task, state);
                    buildOperationContext.failed(state.getFailure());
                    internalTaskListener.afterExecute(legacyOperation, new OperationResult(0, 0, state.getFailure()));
                }
            });
        }
    }
}
