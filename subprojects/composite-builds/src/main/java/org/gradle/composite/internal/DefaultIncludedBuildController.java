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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.BuildResult;
import org.gradle.api.GradleException;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.execution.taskgraph.TaskListenerInternal;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.composite.internal.IncludedBuildTaskResource.State.FAILED;
import static org.gradle.composite.internal.IncludedBuildTaskResource.State.SUCCESS;
import static org.gradle.composite.internal.IncludedBuildTaskResource.State.WAITING;

class DefaultIncludedBuildController implements Runnable, Stoppable, IncludedBuildController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncludedBuildController.class);
    private final IncludedBuildState includedBuild;
    private final ResourceLockCoordinationService coordinationService;

    private enum State {
        CollectingTasks, RunningTasks
    }

    // Fields guarded by lock
    private final Lock lock = new ReentrantLock();
    private final Condition stateChange = lock.newCondition();
    private final Map<String, TaskState> tasks = Maps.newLinkedHashMap();
    private final Set<String> tasksAdded = Sets.newHashSet();
    private final List<Throwable> taskFailures = new ArrayList<Throwable>();
    private State state = State.CollectingTasks;
    private boolean stopRequested;

    public DefaultIncludedBuildController(IncludedBuildState includedBuild, ResourceLockCoordinationService coordinationService) {
        this.includedBuild = includedBuild;
        this.coordinationService = coordinationService;
    }

    @Override
    public boolean populateTaskGraph() {
        Set<String> tasksToExecute = Sets.newLinkedHashSet();
        lock.lock();
        try {
            if (state != State.CollectingTasks) {
                throw new IllegalStateException();
            }
            for (Map.Entry<String, TaskState> taskEntry : tasks.entrySet()) {
                if (taskEntry.getValue().status == TaskStatus.QUEUED) {
                    String taskName = taskEntry.getKey();
                    if (tasksAdded.add(taskName)) {
                        tasksToExecute.add(taskName);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        if (tasksToExecute.isEmpty()) {
            return false;
        }
        includedBuild.addTasks(tasksToExecute);
        return true;
    }

    @Override
    public void run() {
        while (true) {
            Set<String> tasksToExecute = getQueuedTasks();
            if (tasksToExecute == null) {
                return;
            }
            try {
                doBuild(tasksToExecute);
            } finally {
                setState(State.CollectingTasks);
            }
        }
    }

    private void setState(State state) {
        lock.lock();
        try {
            this.state = state;
            stateChange.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void startTaskExecution() {
        setState(State.RunningTasks);
    }

    @Override
    public void awaitTaskCompletion(Collection<? super Throwable> taskFailures) {
        lock.lock();
        try {
            while (state == State.RunningTasks) {
                try {
                    stateChange.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            taskFailures.addAll(this.taskFailures);
            this.taskFailures.clear();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        ArrayList<Throwable> failures = new ArrayList<Throwable>();
        awaitTaskCompletion(failures);
        if (!failures.isEmpty()) {
            throw new MultipleBuildFailures(failures);
        }
        lock.lock();
        try {
            stopRequested = true;
            stateChange.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Nullable
    private Set<String> getQueuedTasks() {
        lock.lock();
        try {
            while (state == State.CollectingTasks && !stopRequested) {
                try {
                    stateChange.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
            if (stopRequested) {
                return null;
            }
            Set<String> tasksToExecute = Sets.newLinkedHashSet();
            for (Map.Entry<String, TaskState> taskEntry : tasks.entrySet()) {
                if (taskEntry.getValue().status == TaskStatus.QUEUED) {
                    tasksToExecute.add(taskEntry.getKey());
                    taskEntry.getValue().status = TaskStatus.EXECUTING;
                }
            }
            return tasksToExecute;
        } finally {
            lock.unlock();
        }
    }

    private void doBuild(final Collection<String> tasksToExecute) {
        if (tasksToExecute.isEmpty()) {
            return;
        }
        LOGGER.info("Executing " + includedBuild.getName() + " tasks " + tasksToExecute);
        IncludedBuildExecutionListener listener = new IncludedBuildExecutionListener(tasksToExecute);
        try {
            includedBuild.execute(tasksToExecute, listener);
            tasksDone(tasksToExecute, null);
        } catch (RuntimeException failure) {
            tasksDone(tasksToExecute, failure);
        }
    }

    private void taskCompleted(String task, Throwable failure) {
        lock.lock();
        try {
            TaskState taskState = tasks.get(task);
            if (taskState == null) {
                taskState = new TaskState();
                tasks.put(task, taskState);
            }
            taskState.status = failure == null ? TaskStatus.SUCCESS : TaskStatus.FAILED;
        } finally {
            lock.unlock();
        }
        // Notify threads that may be waiting on this task to complete.
        // This is required because although all builds may share the same coordination service, the 'something may have changed' event that is fired when a task in this build completes
        // happens before the state tracked here is updated, and so the worker threads in the consuming build may think the task has not completed and go back to sleep waiting for some
        // other event to happen, which may not. Signalling again here means that all worker threads in all builds will be woken up which can be expensive.
        // It would be much better to avoid duplicating the task state here and instead have the task executors communicate directly with each other, possibly via some abstraction
        // that represents the task outcome
        coordinationService.notifyStateChange();
    }

    private void tasksDone(Collection<String> tasksExecuted, @Nullable RuntimeException failure) {
        boolean someTasksNotCompleted = false;
        lock.lock();
        try {
            for (String task : tasksExecuted) {
                TaskState taskState = tasks.get(task);
                if (taskState.status == TaskStatus.EXECUTING) {
                    taskState.status = TaskStatus.FAILED;
                    someTasksNotCompleted = true;
                }
            }
            if (failure != null) {
                if (failure instanceof MultipleBuildFailures) {
                    taskFailures.addAll(((MultipleBuildFailures) failure).getCauses());
                } else {
                    taskFailures.add(failure);
                }
            }
        } finally {
            lock.unlock();
        }
        if (someTasksNotCompleted) {
            // See the comment in #taskCompleted, above, for why this is here and why this is a problem
            coordinationService.notifyStateChange();
        }
    }

    @Override
    public void queueForExecution(String taskPath) {
        lock.lock();
        try {
            if (state != State.CollectingTasks) {
                throw new IllegalStateException();
            }
            if (!tasks.containsKey(taskPath)) {
                tasks.put(taskPath, new TaskState());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public IncludedBuildTaskResource.State getTaskState(String taskPath) {
        lock.lock();
        try {
            TaskState state = tasks.get(taskPath);
            if (state == null) {
                throw new IllegalStateException("Included build task '" + taskPath + "' was never scheduled for execution.");
            }
            if (state.status == TaskStatus.FAILED) {
                return FAILED;
            }
            if (state.status == TaskStatus.SUCCESS) {
                return SUCCESS;
            }
            return WAITING;
        } finally {
            lock.unlock();
        }
    }

    private enum TaskStatus {QUEUED, EXECUTING, FAILED, SUCCESS}

    private static class TaskState {
        public BuildResult result;
        public TaskStatus status = TaskStatus.QUEUED;
    }

    private class IncludedBuildExecutionListener implements TaskExecutionGraphListener, TaskListenerInternal {
        private final Collection<String> tasksToExecute;

        IncludedBuildExecutionListener(Collection<String> tasksToExecute) {
            this.tasksToExecute = tasksToExecute;
        }

        @Override
        public void graphPopulated(TaskExecutionGraph taskExecutionGraph) {
            for (String task : tasksToExecute) {
                if (!taskExecutionGraph.hasTask(task)) {
                    throw new GradleException("Task '" + task + "' not found in build '" + includedBuild.getName() + "'.");
                }
            }
        }

        @Override
        public void beforeExecute(TaskIdentity<?> taskIdentity) {
        }

        @Override
        public void afterExecute(TaskIdentity<?> taskIdentity, org.gradle.api.tasks.TaskState state) {
            Throwable failure = state.getFailure();
            taskCompleted(taskIdentity.getTaskPath(), failure);
        }
    }
}
