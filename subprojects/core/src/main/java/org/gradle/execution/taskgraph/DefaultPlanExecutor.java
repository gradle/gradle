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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.MutableReference;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock;
import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;
import static org.gradle.internal.resources.ResourceLockState.Disposition.RETRY;

@NonNullApi
public class DefaultPlanExecutor implements PlanExecutor {
    private static final Logger LOGGER = Logging.getLogger(DefaultPlanExecutor.class);
    private final int executorCount;
    private final ExecutorFactory executorFactory;
    private final WorkerLeaseService workerLeaseService;
    private final BuildCancellationToken cancellationToken;
    private final ResourceLockCoordinationService coordinationService;

    public DefaultPlanExecutor(ParallelismConfiguration parallelismConfiguration, ExecutorFactory executorFactory, WorkerLeaseService workerLeaseService, BuildCancellationToken cancellationToken, ResourceLockCoordinationService coordinationService) {
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
    public void process(ExecutionPlan executionPlan, Collection<? super Throwable> failures, Action<WorkInfo> worker) {
        ManagedExecutor executor = executorFactory.create("Execution worker for '" + executionPlan.getDisplayName() + "'");
        try {
            WorkerLease parentWorkerLease = workerLeaseService.getCurrentWorkerLease();
            startAdditionalWorkers(executionPlan, worker, executor, parentWorkerLease);
            new ExecutorWorker(executionPlan, worker, parentWorkerLease, cancellationToken, coordinationService).run();
            awaitCompletion(executionPlan, failures);
        } finally {
            executor.stop();
        }
    }

    /**
     * Blocks until all nodes in the plan have been processed. This method will only return when every node in the plan has either completed, failed or been skipped.
     */
    private void awaitCompletion(final ExecutionPlan executionPlan, final Collection<? super Throwable> failures) {
        coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
            @Override
            public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                if (executionPlan.allWorkComplete()) {
                    executionPlan.collectFailures(failures);
                    return FINISHED;
                } else {
                    return RETRY;
                }
            }
        });
    }

    private void startAdditionalWorkers(ExecutionPlan executionPlan, Action<? super WorkInfo> workExecutor, Executor executor, WorkerLease parentWorkerLease) {
        LOGGER.debug("Using {} parallel executor threads", executorCount);

        for (int i = 1; i < executorCount; i++) {
            executor.execute(new ExecutorWorker(executionPlan, workExecutor, parentWorkerLease, cancellationToken, coordinationService));
        }
    }

    private static class ExecutorWorker implements Runnable {
        private final ExecutionPlan executionPlan;
        private final Action<? super WorkInfo> workExecutor;
        private final WorkerLease parentWorkerLease;
        private final BuildCancellationToken cancellationToken;
        private final ResourceLockCoordinationService coordinationService;

        private ExecutorWorker(ExecutionPlan executionPlan, Action<? super WorkInfo> workExecutor, WorkerLease parentWorkerLease, BuildCancellationToken cancellationToken, ResourceLockCoordinationService coordinationService) {
            this.executionPlan = executionPlan;
            this.workExecutor = workExecutor;
            this.parentWorkerLease = parentWorkerLease;
            this.cancellationToken = cancellationToken;
            this.coordinationService = coordinationService;
        }

        @Override
        public void run() {
            final AtomicLong busy = new AtomicLong(0);
            Timer totalTimer = Time.startTimer();
            final Timer executionTimer = Time.startTimer();

            WorkerLease childLease = parentWorkerLease.createChild();
            while (true) {
                boolean moreToExecute = executeWithWork(childLease, new Action<WorkInfo>() {
                    @Override
                    public void execute(WorkInfo work) {
                        LOGGER.info("{} ({}) started.", work, Thread.currentThread());
                        executionTimer.reset();
                        workExecutor.execute(work);
                        long duration = executionTimer.getElapsedMillis();
                        busy.addAndGet(duration);
                        if (LOGGER.isInfoEnabled()) {
                            LOGGER.info("{} ({}) completed. Took {}.", work, Thread.currentThread(), TimeFormatting.formatDurationVerbose(duration));
                        }
                    }
                });
                if (!moreToExecute) {
                    break;
                }
            }

            long total = totalTimer.getElapsedMillis();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Execution worker [{}] finished, busy: {}, idle: {}", Thread.currentThread(), TimeFormatting.formatDurationVerbose(busy.get()), TimeFormatting.formatDurationVerbose(total - busy.get()));
            }
        }

        /**
         * Selects work that's ready to execute and executes the provided action against it. If no work is ready, blocks until some
         * can be executed. If all work has been executed, returns false.
         *
         * @return true if there are more work waiting to execute, false if all work has been executed.
         */
        private boolean executeWithWork(final WorkerLease workerLease, final Action<WorkInfo> workExecutor) {
            final MutableReference<WorkInfo> selected = MutableReference.empty();
            final MutableBoolean workRemaining = new MutableBoolean();
            coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                @Override
                public ResourceLockState.Disposition transform(ResourceLockState resourceLockState) {
                    if (cancellationToken.isCancellationRequested()) {
                        executionPlan.cancelExecution();
                    }

                    workRemaining.set(executionPlan.hasWorkRemaining());
                    if (!workRemaining.get()) {
                        return FINISHED;
                    }

                    try {
                        selected.set(executionPlan.selectNext(workerLease, resourceLockState));
                    } catch (Throwable t) {
                        resourceLockState.releaseLocks();
                        executionPlan.abortAllAndFail(t);
                        workRemaining.set(false);
                    }

                    if (selected.get() == null && workRemaining.get()) {
                        return RETRY;
                    } else {
                        return FINISHED;
                    }
                }
            });

            WorkInfo selectedWorkInfo = selected.get();
            if (selectedWorkInfo != null) {
                execute(selectedWorkInfo, workerLease, workExecutor);
            }
            return workRemaining.get();
        }

        private void execute(final WorkInfo selected, final WorkerLease workerLease, Action<WorkInfo> workExecutor) {
            try {
                if (!selected.isComplete()) {
                    try {
                        workExecutor.execute(selected);
                    } catch (Throwable e) {
                        selected.setExecutionFailure(e);
                    }
                }
            } finally {
                coordinationService.withStateLock(new Transformer<ResourceLockState.Disposition, ResourceLockState>() {
                    @Override
                    public ResourceLockState.Disposition transform(ResourceLockState state) {
                        executionPlan.workComplete(selected);
                        return unlock(workerLease).transform(state);
                    }
                });
            }
        }
    }
}
