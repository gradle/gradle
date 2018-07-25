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
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionAdapter;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.execution.MultipleBuildFailures;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.concurrent.Stoppable;
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

    public DefaultIncludedBuildController(IncludedBuildState includedBuild) {
        this.includedBuild = includedBuild;
    }

    @Override
    public boolean populateTaskGraph() {
        Set<String> tasksToExecute = Sets.newLinkedHashSet();
        lock.lock();
        try {
            if (state != State.CollectingTasks) {
                throw new IllegalStateException();
            }
            for (Map.Entry<String, TaskState> taskEntry: tasks.entrySet()) {
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
            } catch (ReportedException e) {
                // Ignore: we record failure in the BuildListener during the build
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
            for (Map.Entry<String, TaskState> taskEntry: tasks.entrySet()) {
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
        includedBuild.execute(tasksToExecute, listener);
    }

    private void taskCompleted(String task, Throwable failure) {
        lock.lock();
        try {
            TaskState taskState = tasks.get(task);
            taskState.status = failure == null ? TaskStatus.SUCCESS : TaskStatus.FAILED;
        } finally {
            lock.unlock();
        }
    }

    private void tasksDone(Collection<String> tasksExecuted, BuildResult result) {
        lock.lock();
        try {
            for (String task: tasksExecuted) {
                TaskState taskState = tasks.get(task);
                if (taskState.status == TaskStatus.EXECUTING) {
                    taskState.status = TaskStatus.FAILED;
                }
            }
            if (result.getFailure() != null) {
                if (result.getFailure() instanceof MultipleBuildFailures) {
                    taskFailures.addAll(((MultipleBuildFailures) result.getFailure()).getCauses());
                } else {
                    taskFailures.add(result.getFailure());
                }
            }
        } finally {
            lock.unlock();
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

    private class IncludedBuildExecutionListener extends InternalBuildAdapter implements TaskExecutionGraphListener, BuildListener {
        private final Collection<String> tasksToExecute;

        IncludedBuildExecutionListener(Collection<String> tasksToExecute) {
            this.tasksToExecute = tasksToExecute;
        }

        @Override
        public void graphPopulated(TaskExecutionGraph taskExecutionGraph) {
            for (String task: tasksToExecute) {
                if (!taskExecutionGraph.hasTask(task)) {
                    throw new GradleException("Task '" + task + "' not found in build '" + includedBuild.getName() + "'.");
                }
            }

            taskExecutionGraph.addTaskExecutionListener(new TaskCompletionRecorder());
        }

        @Override
        public void buildFinished(BuildResult result) {
            tasksDone(tasksToExecute, result);
        }

        private class TaskCompletionRecorder extends TaskExecutionAdapter {
            @Override
            public void afterExecute(Task task, org.gradle.api.tasks.TaskState state) {
                String taskPath = task.getPath();
                Throwable failure = state.getFailure();
                if (tasksToExecute.contains(taskPath)) {
                    taskCompleted(taskPath, failure);
                }
            }
        }
    }
}
