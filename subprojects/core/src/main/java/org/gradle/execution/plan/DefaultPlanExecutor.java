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
import org.gradle.internal.Cast;
import org.gradle.internal.MutableReference;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.TimeFormatting;
import org.gradle.internal.time.Timer;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.internal.work.WorkerLeaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;
import static org.gradle.internal.resources.ResourceLockState.Disposition.RETRY;

@NonNullApi
public class DefaultPlanExecutor implements PlanExecutor, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultPlanExecutor.class);
    private final int executorCount;
    private final WorkerLeaseService workerLeaseService;
    private final BuildCancellationToken cancellationToken;
    private final ResourceLockCoordinationService coordinationService;
    private final ManagedExecutor executor;
    private final MergedQueues queue;
    private final AtomicBoolean workersStarted = new AtomicBoolean();

    public DefaultPlanExecutor(ParallelismConfiguration parallelismConfiguration, ExecutorFactory executorFactory, WorkerLeaseService workerLeaseService, BuildCancellationToken cancellationToken, ResourceLockCoordinationService coordinationService) {
        this.cancellationToken = cancellationToken;
        this.coordinationService = coordinationService;
        int numberOfParallelExecutors = parallelismConfiguration.getMaxWorkerCount();
        if (numberOfParallelExecutors < 1) {
            throw new IllegalArgumentException("Not a valid number of parallel executors: " + numberOfParallelExecutors);
        }

        this.executorCount = numberOfParallelExecutors;
        this.workerLeaseService = workerLeaseService;
        this.queue = new MergedQueues(coordinationService, false);
        this.executor = executorFactory.create("Execution worker");
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(queue, executor).stop();
    }

    @Override
    public <T> ExecutionResult<Void> process(WorkSource<T> workSource, Action<T> worker) {
        PlanDetails planDetails = new PlanDetails(Cast.uncheckedCast(workSource), Cast.uncheckedCast(worker));
        queue.add(planDetails);

        maybeStartWorkers(queue, executor);

        // Run work from the plan from this thread as well, given that it will be blocked waiting for it to complete anyway
        WorkerLease currentWorkerLease = workerLeaseService.getCurrentWorkerLease();
        MergedQueues thisPlanOnly = new MergedQueues(coordinationService, true);
        thisPlanOnly.add(planDetails);
        new ExecutorWorker(thisPlanOnly, currentWorkerLease, cancellationToken, coordinationService, workerLeaseService).run();

        List<Throwable> failures = new ArrayList<>();
        awaitCompletion(workSource, currentWorkerLease, failures);
        return ExecutionResult.maybeFailed(failures);
    }

    @Override
    public void assertHealthy() {
        coordinationService.withStateLock(queue::assertHealthy);
    }

    /**
     * Blocks until all items in the queue have been processed. This method will only return when every item in the queue has either completed, failed or been skipped.
     */
    private void awaitCompletion(WorkSource<?> workSource, WorkerLease workerLease, Collection<? super Throwable> failures) {
        coordinationService.withStateLock(resourceLockState -> {
            if (workSource.allExecutionComplete()) {
                // Need to hold a worker lease in order to finish up
                if (!workerLease.isLockedByCurrentThread()) {
                    if (!workerLease.tryLock()) {
                        return RETRY;
                    }
                }
                workSource.collectFailures(failures);
                queue.removeFinishedPlans();
                return FINISHED;
            } else {
                // Release worker lease (if held) while waiting for work to complete
                workerLease.unlock();
                return RETRY;
            }
        });
    }

    private void maybeStartWorkers(MergedQueues queue, Executor executor) {
        if (workersStarted.compareAndSet(false, true)) {
            LOGGER.debug("Using {} parallel executor threads", executorCount);
            for (int i = 1; i < executorCount; i++) {
                executor.execute(new ExecutorWorker(queue, null, cancellationToken, coordinationService, workerLeaseService));
            }
        }
    }

    private static class PlanDetails {
        final WorkSource<Object> source;
        final Action<Object> worker;

        public PlanDetails(WorkSource<Object> source, Action<Object> worker) {
            this.source = source;
            this.worker = worker;
        }
    }

    private static class WorkItem {
        final WorkSource.Selection<Object> selection;
        final WorkSource<Object> plan;
        final Action<Object> executor;

        public WorkItem(WorkSource.Selection<Object> selection, WorkSource<Object> plan, Action<Object> executor) {
            this.selection = selection;
            this.plan = plan;
            this.executor = executor;
        }
    }

    private static class MergedQueues implements Closeable {
        private final ResourceLockCoordinationService coordinationService;
        private final boolean autoFinish;
        private boolean finished;
        private final LinkedList<PlanDetails> queues = new LinkedList<>();

        public MergedQueues(ResourceLockCoordinationService coordinationService, boolean autoFinish) {
            this.coordinationService = coordinationService;
            this.autoFinish = autoFinish;
        }

        public WorkSource.State executionState() {
            coordinationService.assertHasStateLock();
            Iterator<PlanDetails> iterator = queues.iterator();
            while (iterator.hasNext()) {
                PlanDetails details = iterator.next();
                WorkSource.State state = details.source.executionState();
                if (state == WorkSource.State.NoMoreWorkToStart) {
                    if (details.source.allExecutionComplete()) {
                        iterator.remove();
                    }
                    // Else, leave the plan in the set of plans so that it can participate in health monitoring. It will be garbage collected once complete
                } else if (state == WorkSource.State.MaybeWorkReadyToStart) {
                    return WorkSource.State.MaybeWorkReadyToStart;
                }
            }
            if (nothingMoreToStart()) {
                return WorkSource.State.NoMoreWorkToStart;
            } else {
                return WorkSource.State.NoWorkReadyToStart;
            }
        }

        public WorkSource.Selection<WorkItem> selectNext() {
            coordinationService.assertHasStateLock();
            Iterator<PlanDetails> iterator = queues.iterator();
            while (iterator.hasNext()) {
                PlanDetails details = iterator.next();
                WorkSource.Selection<Object> selection = details.source.selectNext();
                if (selection.isNoMoreWorkToStart()) {
                    if (details.source.allExecutionComplete()) {
                        iterator.remove();
                    }
                    // Else, leave the plan in the set of plans so that it can participate in health monitoring. It will be garbage collected once complete
                } else if (!selection.isNoWorkReadyToStart()) {
                    return WorkSource.Selection.of(new WorkItem(selection, details.source, details.worker));
                }
            }
            if (nothingMoreToStart()) {
                return WorkSource.Selection.noMoreWorkToStart();
            } else {
                return WorkSource.Selection.noWorkReadyToStart();
            }
        }

        private boolean nothingMoreToStart() {
            return finished || (autoFinish && queues.isEmpty());
        }

        public void add(PlanDetails planDetails) {
            coordinationService.withStateLock(() -> {
                if (finished) {
                    throw new IllegalStateException("This queue has been closed.");
                }
                // Assume that the plan is required by those plans already running and add to the head of the queue
                queues.addFirst(planDetails);
                // Signal to the worker threads that work may be available
                coordinationService.notifyStateChange();
            });
        }

        public void removeFinishedPlans() {
            coordinationService.assertHasStateLock();
            queues.removeIf(details -> details.source.allExecutionComplete());
        }

        @Override
        public void close() throws IOException {
            coordinationService.withStateLock(() -> {
                finished = true;
                if (!queues.isEmpty()) {
                    throw new IllegalStateException("Not all work has completed.");
                }
                // Signal to the worker threads that no more work is available
                coordinationService.notifyStateChange();
            });
        }

        public void cancelExecution() {
            coordinationService.assertHasStateLock();
            for (PlanDetails details : queues) {
                details.source.cancelExecution();
            }
        }

        public void abortAllAndFail(Throwable t) {
            coordinationService.assertHasStateLock();
            for (PlanDetails details : queues) {
                details.source.abortAllAndFail(t);
            }
        }

        public void assertHealthy() {
            coordinationService.assertHasStateLock();
            if (queues.isEmpty()) {
                return;
            }
            List<WorkSource.Diagnostics> allDiagnostics = new ArrayList<>(queues.size());
            for (PlanDetails details : queues) {
                WorkSource.Diagnostics diagnostics = details.source.healthDiagnostics();
                if (diagnostics.canMakeProgress()) {
                    return;
                }
                allDiagnostics.add(diagnostics);
            }

            // Log some diagnostic information to the console, in addition to aborting execution with an exception which will also be logged
            // Given that the execution infrastructure is in an unhealthy state, it may not shut down cleanly and report the execution.
            // So, log some details here just in case
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Unable to make progress running work. The following items are queued for execution but none of them can be started:");
            formatter.startChildren();
            for (WorkSource.Diagnostics diagnostics : allDiagnostics) {
                diagnostics.describeTo(formatter);
            }
            formatter.endChildren();
            System.out.println(formatter);

            IllegalStateException failure = new IllegalStateException("Unable to make progress running work. There are items queued for execution but none of them can be started");
            abortAllAndFail(failure);
            coordinationService.notifyStateChange();
        }
    }

    private static class ExecutorWorker implements Runnable {
        private final MergedQueues queue;
        private WorkerLease workerLease;
        private final BuildCancellationToken cancellationToken;
        private final ResourceLockCoordinationService coordinationService;
        private final WorkerLeaseService workerLeaseService;

        private ExecutorWorker(
            MergedQueues queue,
            @Nullable WorkerLease workerLease,
            BuildCancellationToken cancellationToken,
            ResourceLockCoordinationService coordinationService,
            WorkerLeaseService workerLeaseService
        ) {
            this.queue = queue;
            this.workerLease = workerLease;
            this.cancellationToken = cancellationToken;
            this.coordinationService = coordinationService;
            this.workerLeaseService = workerLeaseService;
        }

        @Override
        public void run() {
            final AtomicLong busy = new AtomicLong(0);
            Timer totalTimer = Time.startTimer();
            final Timer executionTimer = Time.startTimer();

            boolean releaseLeaseOnCompletion;
            if (workerLease == null) {
                workerLease = workerLeaseService.newWorkerLease();
                releaseLeaseOnCompletion = true;
            } else {
                releaseLeaseOnCompletion = false;
            }

            while (true) {
                WorkItem workItem = getNextItem(workerLease);
                if (workItem == null) {
                    break;
                }
                Object selected = workItem.selection.getItem();
                LOGGER.info("{} ({}) started.", selected, Thread.currentThread());
                executionTimer.reset();
                execute(selected, workItem.plan, workItem.executor);
                long duration = executionTimer.getElapsedMillis();
                busy.addAndGet(duration);
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("{} ({}) completed. Took {}.", selected, Thread.currentThread(), TimeFormatting.formatDurationVerbose(duration));
                }
            }

            if (releaseLeaseOnCompletion) {
                coordinationService.withStateLock(() -> workerLease.unlock());
            }

            long total = totalTimer.getElapsedMillis();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Execution worker [{}] finished, busy: {}, idle: {}", Thread.currentThread(), TimeFormatting.formatDurationVerbose(busy.get()), TimeFormatting.formatDurationVerbose(total - busy.get()));
            }
        }

        /**
         * Selects an item that's ready to execute and executes the provided action against it. If no item is ready, blocks until some
         * can be executed.
         *
         * @return The next item to execute or {@code null} when there are no items remaining
         */
        @Nullable
        private WorkItem getNextItem(final WorkerLease workerLease) {
            final MutableReference<WorkItem> selected = MutableReference.empty();
            coordinationService.withStateLock(resourceLockState -> {
                if (cancellationToken.isCancellationRequested()) {
                    queue.cancelExecution();
                }

                WorkSource.State state = queue.executionState();
                if (state == WorkSource.State.NoMoreWorkToStart) {
                    return FINISHED;
                } else if (state == WorkSource.State.NoWorkReadyToStart) {
                    // Release worker lease while waiting
                    if (workerLease.isLockedByCurrentThread()) {
                        workerLease.unlock();
                    }
                    return RETRY;
                }
                // Else there may be items ready, acquire a worker lease

                boolean hasWorkerLease = workerLease.isLockedByCurrentThread();
                if (!hasWorkerLease && !workerLease.tryLock()) {
                    // Cannot get a lease to run work
                    return RETRY;
                }

                WorkSource.Selection<WorkItem> workItem;
                try {
                    workItem = queue.selectNext();
                } catch (Throwable t) {
                    resourceLockState.releaseLocks();
                    queue.abortAllAndFail(t);
                    return FINISHED;
                }
                if (workItem.isNoMoreWorkToStart()) {
                    return FINISHED;
                } else if (workItem.isNoWorkReadyToStart()) {
                    // Release worker lease while waiting
                    workerLease.unlock();
                    return RETRY;
                }

                selected.set(workItem.getItem());
                return FINISHED;
            });

            return selected.get();
        }

        private void execute(Object selected, WorkSource<Object> executionPlan, Action<Object> worker) {
            Throwable failure = null;
            try {
                try {
                    worker.execute(selected);
                } catch (Throwable t) {
                    failure = t;
                }
            } finally {
                markFinished(selected, executionPlan, failure);
            }
        }

        private void markFinished(Object selected, WorkSource<Object> executionPlan, @Nullable Throwable failure) {
            coordinationService.withStateLock(() -> {
                try {
                    executionPlan.finishedExecuting(selected, failure);
                } catch (Throwable t) {
                    queue.abortAllAndFail(t);
                }
                // Notify other threads that the item is finished as this may unblock further work
                // or this might be the last item in the queue
                coordinationService.notifyStateChange();
            });
        }
    }
}
