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
import org.gradle.BuildAdapter;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionAdapter;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class DefaultIncludedBuildController implements Runnable, Stoppable, IncludedBuildController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncludedBuildController.class);
    private final IncludedBuildInternal includedBuild;

    private final Map<String, TaskState> tasks = Maps.newLinkedHashMap();
    private final Set<String> tasksAdded = Sets.newHashSet();

    // Fields guarded by lock
    private final Lock lock = new ReentrantLock();
    private final Condition taskQueued = lock.newCondition();
    private final Condition taskCompleted = lock.newCondition();

    private final CountDownLatch started = new CountDownLatch(1);
    private final AtomicBoolean stopRequested = new AtomicBoolean();
    private final CountDownLatch stopped = new CountDownLatch(1);

    public DefaultIncludedBuildController(IncludedBuild includedBuild) {
        this.includedBuild = (IncludedBuildInternal) includedBuild;
    }

    @Override
    public boolean populateTaskGraph() {
        Set<String> tasksToExecute = Sets.newLinkedHashSet();
        for (Map.Entry<String, TaskState> taskEntry : tasks.entrySet()) {
            if (taskEntry.getValue().status == TaskStatus.QUEUED) {
                String taskName = taskEntry.getKey();
                if (tasksAdded.add(taskName)) {
                    tasksToExecute.add(taskName);
                }
            }
        }
        if (tasksToExecute.isEmpty()) {
            return false;
        }
        includedBuild.addTasks(tasksToExecute);
        return true;
    }

    @Override
    public void run() {
        try {
            started.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        while (!stopRequested.get()) {
            Set<String> tasksToExecute = getQueuedTasks();
            try {
                doBuild(tasksToExecute);
            } catch (ReportedException e) {
                // Ignore: we record failure in the BuildListener during the build
            }
        }
        stopped.countDown();
    }

    @Override
    public void startTaskExecution() {
        started.countDown();
    }

    @Override
    public void stopTaskExecution() {
        stop();
    }

    public void stop() {
        stopRequested.set(true);
        started.countDown();

        lock.lock();
        try {
            taskQueued.signalAll();
        } finally {
            lock.unlock();
        }

        try {
            stopped.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private Set<String> getQueuedTasks() {
        lock.lock();
        try {
            while (!stopRequested.get()) {
                Set<String> tasksToExecute = Sets.newLinkedHashSet();
                for (Map.Entry<String, TaskState> taskEntry : tasks.entrySet()) {
                    if (taskEntry.getValue().status == TaskStatus.QUEUED) {
                        tasksToExecute.add(taskEntry.getKey());
                        taskEntry.getValue().status = TaskStatus.EXECUTING;
                    }
                }
                if (!tasksToExecute.isEmpty()) {
                    return tasksToExecute;
                }

                try {
                    taskQueued.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }

        return Collections.emptySet();
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
            taskState.failure = failure;
            taskCompleted.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private void buildFailed(Collection<String> tasksExecuted, Throwable failure) {
        lock.lock();
        try {
            for (String task : tasksExecuted) {
                TaskState taskState = tasks.get(task);
                if (taskState.status == TaskStatus.EXECUTING) {
                    taskState.status = TaskStatus.FAILED;
                    taskState.failure = failure;
                }
            }
            taskCompleted.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void queueForExecution(String taskPath) {
        lock.lock();
        try {
            if (!tasks.containsKey(taskPath)) {
                tasks.put(taskPath, new TaskState());
                taskQueued.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void awaitCompletion(String taskPath) {
        lock.lock();
        try {
            while (!isComplete(taskPath)) {
                taskCompleted.await();
            }
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            lock.unlock();
        }
    }

    public boolean isComplete(String taskPath) {
        lock.lock();
        try {
            TaskState state = tasks.get(taskPath);
            if (state == null) {
                throw new IllegalStateException("Included build task '" + taskPath + "' was never scheduled for execution.");
            }
            if (state.status == TaskStatus.FAILED) {
                throw UncheckedException.throwAsUncheckedException(state.failure);
            }
            return state.status == TaskStatus.SUCCESS;
        } finally {
            lock.unlock();
        }
    }

    private enum TaskStatus { QUEUED, EXECUTING, FAILED, SUCCESS }

    private static class TaskState {
        public BuildResult result;
        public Throwable failure;
        public TaskStatus status = TaskStatus.QUEUED;

        public void rethrowFailure() {
            if (failure != null) {
                throw UncheckedException.throwAsUncheckedException(failure);
            }
        }
    }

    private class IncludedBuildExecutionListener extends BuildAdapter implements TaskExecutionGraphListener, BuildListener {
        private final Collection<String> tasksToExecute;

        public IncludedBuildExecutionListener(Collection<String> tasksToExecute) {
            this.tasksToExecute = tasksToExecute;
        }

        @Override
        public void graphPopulated(TaskExecutionGraph taskExecutionGraph) {
            for (String task : tasksToExecute) {
                if (!taskExecutionGraph.hasTask(task)) {
                    throw new GradleException("Task '" + task + "' not found in build '" + includedBuild.getName() + "'.");
                }
            }

            taskExecutionGraph.addTaskExecutionListener(new TaskCompletionRecorder());
        }

        @Override
        public void buildFinished(BuildResult result) {
            if (result.getFailure() != null) {
                buildFailed(tasksToExecute, result.getFailure());
            }
        }

        private class TaskCompletionRecorder extends TaskExecutionAdapter {
            @Override
            public void afterExecute(Task task, org.gradle.api.tasks.TaskState state) {
                String taskPath = task.getPath();
                if (tasksToExecute.contains(taskPath)) {
                    Throwable failure = state.getFailure();
                    taskCompleted(taskPath, failure);
                }
            }
        }
    }
}
