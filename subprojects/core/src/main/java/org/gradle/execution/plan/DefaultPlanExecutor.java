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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.Cast;
import org.gradle.internal.MutableReference;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.buildoption.InternalFlag;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.WorkerLimits;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.internal.work.WorkerLeaseService;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ToLongFunction;

import static org.gradle.internal.resources.ResourceLockState.Disposition.FINISHED;
import static org.gradle.internal.resources.ResourceLockState.Disposition.RETRY;

@NonNullApi
public class DefaultPlanExecutor implements PlanExecutor, Stoppable {
    public static final InternalFlag STATS = new InternalFlag("org.gradle.internal.executor.stats");
    private static final Logger LOGGER = Logging.getLogger(DefaultPlanExecutor.class);
    private final WorkerLimits workerLimits;
    private final WorkerLeaseService workerLeaseService;
    private final BuildCancellationToken cancellationToken;
    private final ResourceLockCoordinationService coordinationService;
    private final ManagedExecutor executor;
    private final MergedQueues queue;
    private final ExecutorState state = new ExecutorState();
    private final ExecutorStats stats;

    public DefaultPlanExecutor(
        WorkerLimits workerLimits,
        ExecutorFactory executorFactory,
        WorkerLeaseService workerLeaseService,
        BuildCancellationToken cancellationToken,
        ResourceLockCoordinationService coordinationService,
        InternalOptions internalOptions
    ) {
        this.workerLimits = workerLimits;
        this.cancellationToken = cancellationToken;
        this.coordinationService = coordinationService;
        this.workerLeaseService = workerLeaseService;
        this.stats = internalOptions.getOption(STATS).get() ? new CollectingExecutorStats(state) : state;
        this.queue = new MergedQueues(coordinationService, false);
        this.executor = executorFactory.create("Execution worker");
    }

    @Override
    public void stop() {
        try {
            CompositeStoppable.stoppable(queue, executor).stop();
        } finally {
            stats.report();
        }
    }

    @Override
    public <T> ExecutionResult<Void> process(WorkSource<T> workSource, Action<T> worker) {
        PlanDetails planDetails = new PlanDetails(Cast.uncheckedCast(workSource), Cast.uncheckedCast(worker));
        queue.add(planDetails);

        maybeStartWorkers(queue, executor);

        // Run the work from the source from this thread as well, given that it will be blocked waiting for that work to complete anyway
        WorkerLease currentWorkerLease = workerLeaseService.getCurrentWorkerLease();
        MergedQueues thisPlanOnly = new MergedQueues(coordinationService, true);
        thisPlanOnly.add(planDetails);
        new ExecutorWorker(thisPlanOnly, currentWorkerLease, cancellationToken, coordinationService, workerLeaseService, stats).run();

        List<Throwable> failures = new ArrayList<>();
        awaitCompletion(workSource, currentWorkerLease, failures);
        return ExecutionResult.maybeFailed(failures);
    }

    @Override
    public void assertHealthy() {
        // Wait until execution state is healthy.
        // When all workers are waiting for work and work becomes available, there is a small period between signalling the workers and at least one worker waking up and starting work.
        // If the health check is run during that period, it will fail because it appears that all workers are stuck.
        //
        // This situation can happen when the last task in an included build is executed by the thread that called process() and all other workers are waiting for work to be enabled in the
        // other builds in the tree. The thread will signal the other threads and then stop running as a worker. At this point in time, work will be ready to start but all the (other) workers will
        // still be waiting. A short time later, the workers will wake up and start running the work.
        Instant expiry = Instant.now().plus(2, ChronoUnit.SECONDS);
        ExecutorState.HealthState healthState;
        do {
            healthState = coordinationService.withStateLock(() -> state.healthCheck(queue));
            if (healthState == null) {
                // Health is ok
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } while (expiry.compareTo(Instant.now()) > 0);

        // Health is not ok

        // Log some diagnostic information to the console, in addition to aborting execution with an exception that will also be logged
        // Given that the execution infrastructure is in an unhealthy state, it may not shut down cleanly and report the execution.
        // So, log some details here just in case
        System.out.println(healthState.detailMessage);

        IllegalStateException failure = new IllegalStateException("Unable to make progress running work. There are items queued for execution but none of them can be started");
        coordinationService.withStateLock(() -> queue.abortAllAndFail(failure));
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
        int executorCount = workerLimits.getMaxWorkerCount();
        state.maybeStartWorkers(() -> {
            LOGGER.debug("Using {} parallel executor threads", executorCount);
            for (int i = 1; i < executorCount; i++) {
                executor.execute(new ExecutorWorker(queue, null, cancellationToken, coordinationService, workerLeaseService, stats));
            }
        });
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
            coordinationService.notifyStateChange();
        }

        public boolean nothingQueued() {
            coordinationService.assertHasStateLock();
            for (PlanDetails queue : queues) {
                if (queue.source.executionState() != WorkSource.State.NoMoreWorkToStart) {
                    return false;
                }
            }
            return true;
        }

        public void appendHealthDiagnostics(TreeFormatter formatter) {
            coordinationService.assertHasStateLock();

            List<WorkSource.Diagnostics> allDiagnostics = new ArrayList<>(queues.size());
            for (PlanDetails details : queues) {
                allDiagnostics.add(details.source.healthDiagnostics());
            }
            for (WorkSource.Diagnostics diagnostics : allDiagnostics) {
                diagnostics.describeTo(formatter);
            }
        }
    }

    private static class ExecutorWorker implements Runnable {
        private final MergedQueues queue;
        private WorkerLease workerLease;
        private final BuildCancellationToken cancellationToken;
        private final ResourceLockCoordinationService coordinationService;
        private final WorkerLeaseService workerLeaseService;
        private final WorkerStats stats;

        private ExecutorWorker(
            MergedQueues queue,
            @Nullable WorkerLease workerLease,
            BuildCancellationToken cancellationToken,
            ResourceLockCoordinationService coordinationService,
            WorkerLeaseService workerLeaseService,
            ExecutorStats executorStats
        ) {
            this.queue = queue;
            this.workerLease = workerLease;
            this.cancellationToken = cancellationToken;
            this.coordinationService = coordinationService;
            this.workerLeaseService = workerLeaseService;
            this.stats = executorStats.startWorker();
        }

        @Override
        public void run() {
            try {
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
                    execute(selected, workItem.plan, workItem.executor);
                }

                if (releaseLeaseOnCompletion) {
                    coordinationService.withStateLock(() -> workerLease.unlock());
                }
            } finally {
                stats.finish();
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
            final MutableReference<WorkItem> selected;
            stats.startSelect();
            try {
                selected = MutableReference.empty();
                coordinationService.withStateLock(resourceLockState -> {
                    stats.finishWaitingForNextItem();
                    if (cancellationToken.isCancellationRequested()) {
                        queue.cancelExecution();
                    }

                    WorkSource.State state = queue.executionState();
                    if (state == WorkSource.State.NoMoreWorkToStart) {
                        return FINISHED;
                    }

                    if (!workerLease.tryLock()) {
                        // Cannot get a lease to run work
                        // Do not call `startWaitingForNextItem()` as there may be work available but this worker cannot start it, and so should not be considered "waiting for work".
                        // The health monitoring is currently only concerned with whether work can be started.
                        // At some point it could be improved to track the health of all worker threads, not just the plan executor threads
                        return RETRY;
                    }

                    if (state == WorkSource.State.NoWorkReadyToStart) {
                        stats.startWaitingForNextItem();
                        // Release worker lease while waiting
                        workerLease.unlock();
                        return RETRY;
                    }

                    // Have a worker lease and work may be available

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
                        stats.startWaitingForNextItem();
                        // Release worker lease while waiting
                        workerLease.unlock();
                        return RETRY;
                    }

                    selected.set(workItem.getItem());
                    return FINISHED;
                });
            } finally {
                stats.finishSelect();
            }

            return selected.get();
        }

        private void execute(Object selected, WorkSource<Object> executionPlan, Action<Object> worker) {
            Throwable failure = null;
            try {
                stats.startExecute();
                try {
                    worker.execute(selected);
                } catch (Throwable t) {
                    failure = t;
                } finally {
                    stats.finishExecute();
                }
            } finally {
                markFinished(selected, executionPlan, failure);
            }
        }

        private void markFinished(Object selected, WorkSource<Object> executionPlan, @Nullable Throwable failure) {
            stats.startMarkFinished();
            try {
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
            } finally {
                stats.finishMarkFinished();
            }
        }
    }

    /**
     * Implementations must be thread safe.
     */
    private interface ExecutorStats {
        void report();

        WorkerStats startWorker();
    }

    /**
     * Implementations are only used by the worker thead and do not need to be thread safe.
     */
    private interface WorkerState {
        void startWaitingForNextItem();

        void finishWaitingForNextItem();
    }

    /**
     * Implementations are only used by the worker thead and do not need to be thread safe.
     */
    private interface WorkerStats extends WorkerState {
        void startSelect();

        void finishSelect();

        void startExecute();

        void finishExecute();

        void startMarkFinished();

        void finishMarkFinished();

        void finish();
    }

    private static class ExecutorState implements ExecutorStats {
        private final AtomicReference<List<WorkerState>> allWorkers = new AtomicReference<>();

        public void maybeStartWorkers(Runnable startAction) {
            if (allWorkers.get() != null) {
                return;
            }
            if (allWorkers.compareAndSet(null, new CopyOnWriteArrayList<>())) {
                startAction.run();
            }
        }

        @Override
        public WorkerStats startWorker() {
            WorkerState state = new WorkerState();
            allWorkers.get().add(state);
            return state;
        }

        @Override
        public void report() {
        }

        @Nullable
        public HealthState healthCheck(MergedQueues queues) {
            // Execution is healthy when:
            // - There is no work queued. There may be work still being run by workers, assume those workers are healthy (of course this isn't always true but for now assume it is)
            // - There is work queued, and some workers are running ("running" means "not stopped and not waiting for more work"). Assume the workers that are running are healthy

            if (queues.nothingQueued()) {
                return null;
            }

            List<WorkerState> workers = allWorkers.get();
            if (workers == null || workers.isEmpty()) {
                // Workers have not been started yet, assume this is going to happen and that everything is healthy
                return null;
            }

            int waitingWorkers = 0;
            int stoppedWorkers = 0;
            for (WorkerState worker : workers) {
                ExecutionState currentState = worker.state.get();
                if (currentState == ExecutionState.Running) {
                    return null;
                } else if (currentState == ExecutionState.Waiting) {
                    waitingWorkers++;
                } else if (currentState == ExecutionState.Stopped) {
                    stoppedWorkers++;
                }
            }

            // No workers doing anything

            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Unable to make progress running work. The following items are queued for execution but none of them can be started:");
            formatter.startChildren();
            queues.appendHealthDiagnostics(formatter);
            formatter.node("Workers waiting for work: " + waitingWorkers);
            formatter.node("Stopped workers: " + stoppedWorkers);
            formatter.endChildren();
            return new HealthState(formatter);
        }

        enum ExecutionState {
            Running, Waiting, Stopped
        }

        private static class HealthState {
            final TreeFormatter detailMessage;

            public HealthState(TreeFormatter detailMessage) {
                this.detailMessage = detailMessage;
            }
        }

        private static class WorkerState implements WorkerStats {
            private final AtomicReference<ExecutionState> state = new AtomicReference<>(ExecutionState.Running);

            @Override
            public void startSelect() {
            }

            @Override
            public void finishSelect() {
            }

            @Override
            public void startExecute() {
            }

            @Override
            public void finishExecute() {
            }

            @Override
            public void startMarkFinished() {
            }

            @Override
            public void finishMarkFinished() {
            }

            @Override
            public void finish() {
                state.set(ExecutionState.Stopped);
            }

            @Override
            public void startWaitingForNextItem() {
                if (!state.compareAndSet(ExecutionState.Running, ExecutionState.Waiting)) {
                    throw new IllegalStateException("Unexpected state for worker.");
                }
            }

            @Override
            public void finishWaitingForNextItem() {
                if (state.get() == ExecutionState.Stopped) {
                    throw new IllegalStateException("Unexpected state for worker.");
                }
                state.set(ExecutionState.Running);
            }
        }
    }

    private static class CollectingExecutorStats implements ExecutorStats {
        private final List<CollectingWorkerStats> completedWorkers = new CopyOnWriteArrayList<>();
        private final ExecutorState delegate;

        public CollectingExecutorStats(ExecutorState delegate) {
            this.delegate = delegate;
        }

        @Override
        public WorkerStats startWorker() {
            return new CollectingWorkerStats(this, delegate.startWorker());
        }

        void workerFinished(CollectingWorkerStats stats) {
            completedWorkers.add(stats);
        }

        @Override
        public void report() {
            LOGGER.lifecycle("WORKER THREAD STATISTICS");
            int workerCount = completedWorkers.size();
            LOGGER.lifecycle("worker count: " + workerCount);
            if (workerCount > 0) {
                LOGGER.lifecycle("average select time: " + format(stats -> stats.totalSelectTime));
                LOGGER.lifecycle("average execute time: " + format(stats -> stats.totalExecuteTime));
                LOGGER.lifecycle("average finish time: " + format(stats -> stats.totalMarkFinishedTime));
            }
            completedWorkers.clear();
        }

        private String format(ToLongFunction<CollectingWorkerStats> statsProperty) {
            BigDecimal averageNanos = BigDecimal.valueOf(completedWorkers.stream().mapToLong(statsProperty).sum() / completedWorkers.size());
            return DecimalFormat.getNumberInstance().format(averageNanos.divide(BigDecimal.valueOf(1000000), RoundingMode.HALF_UP)) + "ms";
        }
    }

    private static class CollectingWorkerStats implements WorkerStats {
        final long startTime;
        private final CollectingExecutorStats owner;
        private final WorkerState delegate;
        long finishTime;
        long startCurrentOperation;
        long totalSelectTime;
        long totalExecuteTime;
        long totalMarkFinishedTime;

        public CollectingWorkerStats(CollectingExecutorStats owner, WorkerState delegate) {
            this.owner = owner;
            this.delegate = delegate;
            startTime = System.nanoTime();
        }

        @Override
        public void finish() {
            finishTime = System.nanoTime();
            owner.workerFinished(this);
        }

        @Override
        public void startSelect() {
            startCurrentOperation = System.nanoTime();
        }

        @Override
        public void finishSelect() {
            long duration = System.nanoTime() - startCurrentOperation;
            if (duration > 0) {
                totalSelectTime += duration;
            }
        }

        @Override
        public void startExecute() {
            startCurrentOperation = System.nanoTime();
        }

        @Override
        public void finishExecute() {
            long duration = System.nanoTime() - startCurrentOperation;
            if (duration > 0) {
                totalExecuteTime += duration;
            }
        }

        @Override
        public void startMarkFinished() {
            startCurrentOperation = System.nanoTime();
        }

        @Override
        public void finishMarkFinished() {
            long duration = System.nanoTime() - startCurrentOperation;
            if (duration > 0) {
                totalMarkFinishedTime += duration;
            }
        }

        @Override
        public void startWaitingForNextItem() {
            delegate.startWaitingForNextItem();
        }

        @Override
        public void finishWaitingForNextItem() {
            delegate.finishWaitingForNextItem();
        }
    }
}
