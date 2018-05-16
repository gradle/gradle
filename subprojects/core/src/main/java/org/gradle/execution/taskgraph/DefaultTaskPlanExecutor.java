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

import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.Transformer;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockState;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.internal.time.Timer;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock;
import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;
import static org.gradle.internal.resources.ResourceLockState.Disposition.RETRY;

@NonNullApi
public class DefaultTaskPlanExecutor implements TaskPlanExecutor {
    private static final Logger LOGGER = Logging.getLogger(DefaultTaskPlanExecutor.class);
    private final int executorCount;
    private final ExecutorFactory executorFactory;
    private final WorkerLeaseService workerLeaseService;
    private final BuildCancellationToken cancellationToken;
    private final ResourceLockCoordinationService coordinationService;

    public DefaultTaskPlanExecutor(ParallelismConfiguration parallelismConfiguration, ExecutorFactory executorFactory, WorkerLeaseService workerLeaseService, BuildCancellationToken cancellationToken, ResourceLockCoordinationService coordinationService) {
        this.executorFactory = executorFactory;
        this.cancellationToken = cancellationToken;
        this.coordinationService = coordinationService;
        int numberOfParallelExecutors = parallelismConfiguration.getMaxWorkerCount();
        if (numberOfParallelExecutors < 1) {
            throw new IllegalArgumentException("Not a valid number of parallel executors: " + numberOfParallelExecutors);
        }

        this.executorCount = numberOfParallelExecutors;
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public void process(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker, Collection<? super Throwable> taskFailures) {
        ManagedExecutor executor = executorFactory.create("Task worker for '" + taskExecutionPlan.getDisplayName() + "'");
        try {
            WorkerLease parentWorkerLease = workerLeaseService.getCurrentWorkerLease();
            startAdditionalWorkers(taskExecutionPlan, taskWorker, executor, parentWorkerLease);
            new TaskExecutorWorker(taskExecutionPlan, taskWorker, parentWorkerLease, cancellationToken, coordinationService).run();
            awaitCompletion(taskExecutionPlan, taskFailures);
        } finally {
            executor.stop();
        }
    }

    /**
     * Blocks until all tasks in the plan have been processed. This method will only return when every task in the plan has either completed, failed or been skipped.
     */
    private void awaitCompletion(final TaskExecutionPlan taskExecutionPlan, final Collection<? super Throwable> taskFailures) {
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                if (taskExecutionPlan.allTasksComplete()) {
                    taskExecutionPlan.collectFailures(taskFailures);
                    return FINISHED;
                } else {
                    return RETRY;
                }
            }
        });
    }

    private void startAdditionalWorkers(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker, Executor executor, WorkerLease parentWorkerLease) {
        LOGGER.debug("Using {} parallel executor threads", executorCount);

        for (int i = 1; i < executorCount; i++) {
            executor.execute(new TaskExecutorWorker(taskExecutionPlan, taskWorker, parentWorkerLease, cancellationToken, coordinationService));
        }
    }

    private static class TaskExecutorWorker implements Runnable {
        private final TaskExecutionPlan taskExecutionPlan;
        private final Action<? super TaskInternal> taskWorker;
        private final WorkerLease parentWorkerLease;
        private final BuildCancellationToken cancellationToken;
        private final ResourceLockCoordinationService coordinationService;

        private TaskExecutorWorker(TaskExecutionPlan taskExecutionPlan, Action<? super TaskInternal> taskWorker, WorkerLease parentWorkerLease, BuildCancellationToken cancellationToken, ResourceLockCoordinationService coordinationService) {
            this.taskExecutionPlan = taskExecutionPlan;
            this.taskWorker = taskWorker;
            this.parentWorkerLease = parentWorkerLease;
            this.cancellationToken = cancellationToken;
            this.coordinationService = coordinationService;
        }

        @Override
        public void run() {
            final AtomicLong busy = new AtomicLong(0);
            Timer totalTimer = Time.startTimer();
            final Timer taskTimer = Time.startTimer();

            WorkerLease childLease = parentWorkerLease.createChild();
            boolean moreTasksToExecute = true;
            while (moreTasksToExecute) {
                moreTasksToExecute = executeWithTask(childLease, new Action<TaskInternal>() {
                    @Override
                    public void execute(TaskInternal task) {
                        final String taskPath = task.getPath();
                        LOGGER.info("{} ({}) started.", taskPath, Thread.currentThread());
                        taskTimer.reset();
                        taskWorker.execute(task);
                        long taskDuration = taskTimer.getElapsedMillis();
                        busy.addAndGet(taskDuration);
                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info("{} ({}) completed. Took {}.", taskPath, Thread.currentThread(), TimeFormatting.formatDurationVerbose(taskDuration));
                        }
                    }
                });
            }

            long total = totalTimer.getElapsedMillis();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Task worker [{}] finished, busy: {}, idle: {}", Thread.currentThread(), TimeFormatting.formatDurationVerbose(busy.get()), TimeFormatting.formatDurationVerbose(total - busy.get()));
            }
        }

        /**
         * Selects a task that's ready to execute and executes the provided action against it.  If no tasks are ready, blocks until one
         * can be executed.  If all tasks have been executed, returns false.
         *
         * @return true if there are more tasks waiting to execute, false if all tasks have executed.
         */
        private boolean executeWithTask(final WorkerLease workerLease, final Action<TaskInternal> taskExecution) {
            final AtomicReference<TaskInfo> selected = new AtomicReference<TaskInfo>();
            final AtomicBoolean workRemaining = new AtomicBoolean();
            coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                @Override
                public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                    if (cancellationToken.isCancellationRequested()) {
                        taskExecutionPlan.cancelExecution();
                    }

                    workRemaining.set(taskExecutionPlan.hasWorkRemaining());
                    if (!workRemaining.get()) {
                        return FINISHED;
                    }

                    try {
                        selected.set(taskExecutionPlan.selectNextTask(workerLease, resourceLockState));
                    } catch (Throwable t) {
                        resourceLockState.releaseLocks();
                        taskExecutionPlan.abortAllAndFail(t);
                        workRemaining.set(false);
                    }

                    if (selected.get() == null && workRemaining.get()) {
                        return RETRY;
                    } else {
                        return FINISHED;
                    }
                }
            });

            TaskInfo selectedTask = selected.get();
            if (selectedTask != null) {
                execute(selectedTask, workerLease, taskExecution);
            }
            return workRemaining.get();
        }

        private void execute(final TaskInfo selectedTask, final WorkerLease workerLease, Action<TaskInternal> taskExecution) {
            try {
                if (!selectedTask.isComplete()) {
                    try {
                        taskExecution.execute(selectedTask.getTask());
                    } catch (Throwable e) {
                        selectedTask.setExecutionFailure(e);
                    }
                }
            } finally {
                coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                    @Override
                    public ResourceLockState.Disposition transform(ResourceLockState state) {
                        taskExecutionPlan.taskComplete(selectedTask);
                        return unlock(workerLease).transform(state);
                    }
                });
            }
        }
    }
}
