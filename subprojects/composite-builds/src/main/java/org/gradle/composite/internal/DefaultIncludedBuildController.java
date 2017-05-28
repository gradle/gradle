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
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.initialization.includedbuild.IncludedBuildController;
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
    private static final String SHUTDOWN_SIGNAL = " ";

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIncludedBuildController.class);
    private final IncludedBuildInternal includedBuild;

    private final Map<String, TaskState> tasks = Maps.newLinkedHashMap();

    // Fields guarded by lock
    private final Lock lock = new ReentrantLock();
    private final Condition taskQueued = lock.newCondition();
    private final Condition taskCompleted = lock.newCondition();

    public DefaultIncludedBuildController(IncludedBuild includedBuild) {
        this.includedBuild = (IncludedBuildInternal) includedBuild;
    }

    private final CountDownLatch started = new CountDownLatch(1);
    private final AtomicBoolean stopped = new AtomicBoolean();

    @Override
    public void run() {
        try {
            started.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        while (!stopped.get()) {
            Set<String> tasksToExecute = getQueuedTasks();
            try {
                doBuild(tasksToExecute);
                buildCompleted(tasksToExecute, TaskStatus.SUCCESS, null);
            } catch (Throwable e) {
                buildCompleted(tasksToExecute, TaskStatus.FAILED, e);
            }
        }
    }

    public void startTaskExecution() {
        started.countDown();
    }

    public void stop() {
        queueForExecution(SHUTDOWN_SIGNAL);
        started.countDown();
    }

    private Set<String> getQueuedTasks() {
        lock.lock();
        try {
            while (!stopped.get()) {
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

    private void doBuild(Collection<String> tasksToExecute) {
        if (tasksToExecute.isEmpty()) {
            return;
        }
        LOGGER.info("Executing " + includedBuild.getName() + " tasks " + tasksToExecute);
        includedBuild.execute(tasksToExecute);
    }

    private void buildCompleted(Collection<String> tasksExecuted, TaskStatus status, Throwable failure) {
        lock.lock();
        try {
            for (String task : tasksExecuted) {
                TaskState taskState = tasks.get(task);
                taskState.status = status;
                taskState.failure = failure;
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
        // TODO:DAZ We should enforce that all tasks are queued first
        queueForExecution(taskPath);

        // Start task execution if necessary: this is required for building plugin artifacts,
        // since these are build on-demand prior to regular task execution.
        startTaskExecution();

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

    private boolean isComplete(String taskPath) {
        lock.lock();
        try {
            TaskState state = tasks.get(taskPath);
            if (state != null) {
                if (state.status == TaskStatus.SUCCESS) {
                    return true;
                }
                if (state.status == TaskStatus.FAILED) {
                    throw UncheckedException.throwAsUncheckedException(state.failure);
                }
            }
            return false;
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
}
