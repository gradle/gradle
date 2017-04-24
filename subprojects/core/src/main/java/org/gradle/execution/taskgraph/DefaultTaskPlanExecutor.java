/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.Action;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.internal.time.Clock.prettyTime;

class DefaultTaskPlanExecutor implements TaskPlanExecutor {
    private static final Logger LOGGER = Logging.getLogger(DefaultTaskPlanExecutor.class);
    private final int executorCount;
    private final ExecutorFactory executorFactory;
    private final WorkerLeaseService workerLeaseService;
    private final TaskExecutorPool taskExecutorPool = new DefaultTaskExecutorPool();

    public DefaultTaskPlanExecutor(int numberOfParallelExecutors, ExecutorFactory executorFactory, WorkerLeaseService workerLeaseService) {
        this.executorFactory = executorFactory;
        if (numberOfParallelExecutors < 1) {
            throw new IllegalArgumentException("Not a valid number of parallel executors: " + numberOfParallelExecutors);
        }

        this.executorCount = numberOfParallelExecutors;
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public void process(final TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker) {
        StoppableExecutor executor = executorFactory.create("Task worker");
        try {
            final WorkerLease parentWorkerLease = workerLeaseService.getCurrentWorkerLease();
            taskExecutorPool.reset();
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    taskExecutionPlan.processExecutionQueue(taskExecutorPool);
                }
            });
            startAdditionalWorkers(taskExecutionPlan, taskWorker, executor, parentWorkerLease);
            taskWorker(taskExecutionPlan, taskWorker, parentWorkerLease).run();
            taskExecutionPlan.awaitCompletion();
        } finally {
            executor.stop();
        }
    }

    private void startAdditionalWorkers(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker, Executor executor, WorkerLease parentWorkerLease) {
        LOGGER.debug("Using {} parallel executor threads", executorCount);

        for (int i = 1; i < executorCount; i++) {
            Runnable worker = taskWorker(taskExecutionPlan, taskWorker, parentWorkerLease);
            executor.execute(worker);
        }
    }

    private Runnable taskWorker(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker, WorkerLease parentWorkerLease) {
        return new TaskExecutorWorker(taskExecutionPlan, taskWorker, taskExecutorPool, parentWorkerLease);
    }

    static class TaskExecutorWorker implements Runnable, TaskExecutor {
        private final TaskExecutionPlan taskExecutionPlan;
        private final Action<? super TaskInternal> taskWorker;
        private final TaskExecutorPool taskExecutorPool;
        private final WorkerLease workerLease;
        private final Object taskToExecute = new Object();
        private volatile boolean stopped;
        private volatile TaskInfo currentTask;
        private Thread executorThread;

        private TaskExecutorWorker(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker, TaskExecutorPool taskExecutorPool, WorkerLease parentWorkerLease) {
            this.taskExecutionPlan = taskExecutionPlan;
            this.taskWorker = taskWorker;
            this.taskExecutorPool = taskExecutorPool;
            this.workerLease = parentWorkerLease.createChild();
        }

        @Override
        public void executeTask(TaskInfo taskInfo) {
            synchronized (taskToExecute) {
                currentTask = taskInfo;

                // if we received a null task, this is the signal to stop
                if (taskInfo == null) {
                    stopped = true;
                }

                taskToExecute.notify();
            }
        }

        @Override
        public boolean isBusy() {
            synchronized (taskToExecute) {
                return currentTask != null;
            }
        }

        @Override
        public Thread getThread() {
            return executorThread;
        }

        @Override
        public WorkerLease getWorkerLease() {
            return workerLease;
        }

        public void run() {
            executorThread = Thread.currentThread();
            taskExecutorPool.registerExecutor(this);

            final AtomicLong busy = new AtomicLong(0);
            Timer totalTimer = Timers.startTimer();
            final Timer taskTimer = Timers.startTimer();

            try {
                while (true) {
                    synchronized (taskToExecute) {
                        if (!stopped && currentTask == null) {
                            try {
                                taskToExecute.wait();
                            } catch (InterruptedException e) {
                                LOGGER.error("Task worker interrupted", e);
                                throw UncheckedException.throwAsUncheckedException(e);
                            }
                        }

                        if (stopped) {
                            break;
                        }
                    }

                    final String taskPath = currentTask.getTask().getPath();
                    LOGGER.info("{} ({}) started.", taskPath, Thread.currentThread());
                    taskTimer.reset();
                    processTask(currentTask);
                    long taskDuration = taskTimer.getElapsedMillis();
                    busy.addAndGet(taskDuration);
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("{} ({}) completed. Took {}.", taskPath, Thread.currentThread(), prettyTime(taskDuration));
                    }
                }
            } catch (Throwable e) {
                LOGGER.error("Task worker stopped unexpectedly", e);
            }

            taskExecutorPool.removeExecutor(this);

            long total = totalTimer.getElapsedMillis();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Task worker [{}] finished, busy: {}, idle: {}", Thread.currentThread(), prettyTime(busy.get()), prettyTime(total - busy.get()));
            }
        }

        private void processTask(TaskInfo taskInfo) {
            try {
                taskWorker.execute(taskInfo.getTask());
            } catch (Throwable e) {
                taskInfo.setExecutionFailure(e);
            } finally {
                taskExecutionPlan.taskComplete(taskInfo);
                synchronized (taskToExecute) {
                    currentTask = null;
                }
                taskExecutorPool.notifyAvailable();
            }
        }
    }

    static class DefaultTaskExecutorPool implements TaskExecutorPool {
        private final Set<TaskExecutor> taskExecutors = Sets.newConcurrentHashSet();
        private final Object workersAvailable = new Object();
        private final AtomicBoolean stopped = new AtomicBoolean();

        @Override
        public TaskExecutor getAvailableExecutor() {
            while (true) {
                synchronized (workersAvailable) {
                    synchronized (taskExecutors) {
                        if (!taskExecutors.isEmpty()) {
                            for (TaskExecutor taskExecutor : taskExecutors) {
                                if (!taskExecutor.isBusy()) {
                                    return taskExecutor;
                                }
                            }
                        }
                    }
                    waitForAvailableExecutor();
                }
            }
        }

        private void waitForAvailableExecutor() {
            synchronized (workersAvailable) {
                try {
                    workersAvailable.wait();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }

        @Override
        public void registerExecutor(TaskExecutor taskExecutor) {
            taskExecutors.add(taskExecutor);

            if (stopped.get()) {
                taskExecutor.executeTask(null);
            }

            notifyAvailable();
        }

        @Override
        public void notifyAvailable() {
            synchronized (workersAvailable) {
                workersAvailable.notifyAll();
            }
        }

        @Override
        public void removeExecutor(TaskExecutor taskExecutor) {
            taskExecutors.remove(taskExecutor);
        }

        @Override
        public void stop() {
            synchronized (taskExecutors) {
                stopped.set(true);
            }

            for (TaskExecutor executor : taskExecutors) {
                executor.executeTask(null);
            }
        }

        @Override
        public void reset() {
            taskExecutors.clear();
            stopped.set(false);
        }
    }
}
