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

package org.gradle.execution.plan;

import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.MutableReference;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.internal.time.Timer;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.internal.work.WorkerLeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.internal.resources.DefaultResourceLockCoordinationService.unlock;
import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;
import static org.gradle.internal.resources.ResourceLockState.Disposition.RETRY;

@NonNullApi
public class DefaultPlanExecutor implements PlanExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanExecutor.class);
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
    public void process(ExecutionPlan executionPlan, Collection<? super Throwable> failures, Action<Node> nodeExecutor) {
        ManagedExecutor executor = executorFactory.create("Execution worker for '" + executionPlan.getDisplayName() + "'");
        try {
            WorkerLease parentWorkerLease = workerLeaseService.getCurrentWorkerLease();
            startAdditionalWorkers(executionPlan, nodeExecutor, executor, parentWorkerLease);
            new ExecutorWorker(executionPlan, nodeExecutor, parentWorkerLease, cancellationToken, coordinationService).run();
            awaitCompletion(executionPlan, failures);
        } finally {
            executor.stop();
        }
    }

    /**
     * Blocks until all nodes in the plan have been processed. This method will only return when every node in the plan has either completed, failed or been skipped.
     */
    private void awaitCompletion(final ExecutionPlan executionPlan, final Collection<? super Throwable> failures) {
        coordinationService.withStateLock(resourceLockState -> {
            if (executionPlan.allNodesComplete()) {
                executionPlan.collectFailures(failures);
                return FINISHED;
            } else {
                return RETRY;
            }
        });
    }

    private void startAdditionalWorkers(ExecutionPlan executionPlan, Action<? super Node> nodeExecutor, Executor executor, WorkerLease parentWorkerLease) {
        LOGGER.debug("Using {} parallel executor threads", executorCount);

        for (int i = 1; i < executorCount; i++) {
            executor.execute(new ExecutorWorker(executionPlan, nodeExecutor, parentWorkerLease, cancellationToken, coordinationService));
        }
    }

    private static class ExecutorWorker implements Runnable {
        private final ExecutionPlan executionPlan;
        private final Action<? super Node> nodeExecutor;
        private final WorkerLease parentWorkerLease;
        private final BuildCancellationToken cancellationToken;
        private final ResourceLockCoordinationService coordinationService;

        private ExecutorWorker(ExecutionPlan executionPlan, Action<? super Node> nodeExecutor, WorkerLease parentWorkerLease, BuildCancellationToken cancellationToken, ResourceLockCoordinationService coordinationService) {
            this.executionPlan = executionPlan;
            this.nodeExecutor = nodeExecutor;
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
                boolean nodesRemaining = executeNextNode(childLease, work -> {
                    LOGGER.info("{} ({}) started.", work, Thread.currentThread());
                    executionTimer.reset();
                    nodeExecutor.execute(work);
                    long duration = executionTimer.getElapsedMillis();
                    busy.addAndGet(duration);
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("{} ({}) completed. Took {}.", work, Thread.currentThread(), TimeFormatting.formatDurationVerbose(duration));
                    }
                });
                if (!nodesRemaining) {
                    break;
                }
            }

            long total = totalTimer.getElapsedMillis();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Execution worker [{}] finished, busy: {}, idle: {}", Thread.currentThread(), TimeFormatting.formatDurationVerbose(busy.get()), TimeFormatting.formatDurationVerbose(total - busy.get()));
            }
        }

        /**
         * Selects a node that's ready to execute and executes the provided action against it. If no node is ready, blocks until some
         * can be executed.
         *
         * @return {@code true} if there are more nodes waiting to execute, {@code false} if all nodes have been executed.
         */
        private boolean executeNextNode(final WorkerLease workerLease, final Action<Node> nodeExecutor) {
            final MutableReference<Node> selected = MutableReference.empty();
            final MutableBoolean nodesRemaining = new MutableBoolean();
            coordinationService.withStateLock(resourceLockState -> {
                if (cancellationToken.isCancellationRequested()) {
                    executionPlan.cancelExecution();
                }

                nodesRemaining.set(executionPlan.hasNodesRemaining());
                if (!nodesRemaining.get()) {
                    return FINISHED;
                }

                try {
                    selected.set(executionPlan.selectNext(workerLease, resourceLockState));
                } catch (Throwable t) {
                    resourceLockState.releaseLocks();
                    executionPlan.abortAllAndFail(t);
                    nodesRemaining.set(false);
                }

                if (selected.get() == null && nodesRemaining.get()) {
                    return RETRY;
                } else {
                    return FINISHED;
                }
            });

            Node selectedNode = selected.get();
            if (selectedNode != null) {
                execute(selectedNode, workerLease, nodeExecutor);
            }
            return nodesRemaining.get();
        }

        private void execute(final Node selected, final WorkerLease workerLease, Action<Node> nodeExecutor) {
            try {
                if (!selected.isComplete()) {
                    try {
                        nodeExecutor.execute(selected);
                    } catch (Throwable e) {
                        selected.setExecutionFailure(e);
                    }
                }
            } finally {
                coordinationService.withStateLock(state -> {
                    executionPlan.finishedExecuting(selected);
                    return unlock(workerLease).transform(state);
                });
            }
        }
    }
}
