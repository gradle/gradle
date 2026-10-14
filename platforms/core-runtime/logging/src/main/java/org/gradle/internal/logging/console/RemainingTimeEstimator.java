/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.logging.console;

import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.operations.OperationIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.Math.max;

/**
 * Estimates the time remaining in the execution phase from the estimates of the scheduled work and of each work item.
 * <p>
 * The estimate is the larger of the remaining work spread across the peak observed parallelism, and the longest remaining chain of dependent work.
 * It decreases as work runs and completes, and increases when more work is scheduled, such as by a nested build.
 */
public class RemainingTimeEstimator {

    private final WorkDurationEstimator workDurationEstimator;

    private final Map<OperationIdentifier, ScheduledWork> scheduledWork = new HashMap<>();
    private final List<ScheduledWork> topLevelWork = new ArrayList<>();
    private final Map<OperationIdentifier, RunningWork> runningWork = new HashMap<>();
    // Work items that wait on nested work they started, such as a nested build
    private final Map<OperationIdentifier, WaitingWork> waitingWork = new HashMap<>();
    // For work items whose scheduled work is unknown
    private ScheduledWork unknownScheduledWork = new ScheduledWork();
    private long unstartedWorkMillis;
    private int maxParallelism = 1;
    // Set once any work has no estimate, since the remaining time can then no longer be trusted
    private boolean estimateUnavailable;

    public RemainingTimeEstimator(WorkDurationEstimator workDurationEstimator) {
        this.workDurationEstimator = workDurationEstimator;
        topLevelWork.add(unknownScheduledWork);
    }

    /**
     * Records the work scheduled by the given event, such as the tasks of a build.
     *
     * @param waitingWorkId the running work item that started the scheduled work, and now waits on it
     */
    public void workScheduled(ProgressStartEvent schedulingEvent, @Nullable OperationIdentifier waitingWorkId) {
        if (schedulingEvent.getTotalProgress() == 0) {
            // Nothing scheduled, so nothing to estimate
            return;
        }
        Optional<WorkEstimate> estimate = workDurationEstimator.estimate(schedulingEvent);
        if (!estimate.isPresent()) {
            estimateUnavailable = true;
            return;
        }
        ScheduledWork scheduled = new ScheduledWork();
        scheduled.pendingChain(schedulingEvent.getTimestamp(), estimate.get().getCriticalPathMillis());
        scheduledWork.put(schedulingEvent.getProgressOperationId(), scheduled);
        unstartedWorkMillis += estimate.get().getDurationMillis();

        RunningWork waiting = waitingWorkId == null ? null : runningWork.remove(waitingWorkId);
        if (waiting != null) {
            waitingWork.put(waitingWorkId, new WaitingWork(waiting.scheduledWork, waiting.followingMillis, scheduled));
        } else {
            topLevelWork.add(scheduled);
        }
    }

    /**
     * Records the start of a work item.
     *
     * @param schedulingId the operation that scheduled the work item
     */
    public void workStarted(ProgressStartEvent workStartEvent, @Nullable OperationIdentifier schedulingId) {
        if (estimateUnavailable) {
            return;
        }
        Optional<WorkEstimate> estimate = workDurationEstimator.estimate(workStartEvent);
        if (!estimate.isPresent()) {
            estimateUnavailable = true;
            return;
        }
        ScheduledWork scheduled = schedulingId == null ? null : scheduledWork.get(schedulingId);
        if (scheduled != null) {
            scheduled.workStarted(estimate.get().getCriticalPathMillis());
        }
        long durationMillis = estimate.get().getDurationMillis();
        unstartedWorkMillis -= durationMillis;
        runningWork.put(workStartEvent.getProgressOperationId(), new RunningWork(
            scheduled == null ? unknownScheduledWork : scheduled,
            workStartEvent.getTimestamp(),
            durationMillis,
            estimate.get().getCriticalPathMillis() - durationMillis
        ));
        maxParallelism = max(maxParallelism, runningWork.size());
    }

    public void workCompleted(OperationIdentifier workId, long timestamp) {
        RunningWork running = runningWork.remove(workId);
        if (running != null) {
            running.scheduledWork.pendingChain(timestamp, running.followingMillis);
        }
        WaitingWork waiting = waitingWork.remove(workId);
        if (waiting != null) {
            waiting.scheduledWork.pendingChain(timestamp, waiting.followingMillis);
        }
    }

    public void reset() {
        scheduledWork.clear();
        topLevelWork.clear();
        runningWork.clear();
        waitingWork.clear();
        unknownScheduledWork = new ScheduledWork();
        topLevelWork.add(unknownScheduledWork);
        unstartedWorkMillis = 0;
        maxParallelism = 1;
        estimateUnavailable = false;
    }

    /**
     * Estimates the time remaining, in milliseconds, or empty when any work has no estimate.
     */
    public Optional<Long> estimateRemainingMillis(long now) {
        if (estimateUnavailable) {
            return Optional.empty();
        }
        // Running work uses up at most its own estimate, so overrunning work stalls the estimate rather than raising it
        long remainingWork = max(0, unstartedWorkMillis);
        Map<ScheduledWork, Long> runningChains = new HashMap<>();
        for (RunningWork running : runningWork.values()) {
            long remaining = max(0, running.startTime + running.durationMillis - now);
            remainingWork += remaining;
            runningChains.merge(running.scheduledWork, remaining + running.followingMillis, Math::max);
        }
        long remainingChain = 0;
        for (ScheduledWork scheduled : topLevelWork) {
            remainingChain = max(remainingChain, remainingChain(scheduled, runningChains, now));
        }
        return Optional.of(max(remainingWork / maxParallelism, remainingChain));
    }

    private long remainingChain(ScheduledWork scheduled, Map<ScheduledWork, Long> runningChains, long now) {
        long remaining = max(scheduled.pendingChainEndTime() - now, runningChains.getOrDefault(scheduled, 0L));
        for (WaitingWork waiting : waitingWork.values()) {
            if (waiting.scheduledWork == scheduled) {
                remaining = max(remaining, waiting.followingMillis + remainingChain(waiting.nestedWork, runningChains, now));
            }
        }
        return max(0, remaining);
    }

    /**
     * Work scheduled together, such as the tasks of a build.
     */
    private static class ScheduledWork {
        // Chains of work that has not started yet, such as the critical path before any work has started,
        // or the work that runs after a completed work item
        private final List<PendingChain> pendingChains = new ArrayList<>();

        void pendingChain(long startTime, long durationMillis) {
            // Chains that have already ended no longer affect the estimate
            pendingChains.removeIf(chain -> chain.endTime <= startTime);
            if (durationMillis > 0) {
                pendingChains.add(new PendingChain(startTime + durationMillis, durationMillis));
            }
        }

        /**
         * A started work item carries the chains that are no longer than its own, so they no longer hold up the estimate when work finishes earlier than estimated.
         */
        void workStarted(long criticalPathMillis) {
            pendingChains.removeIf(chain -> chain.durationMillis <= criticalPathMillis);
        }

        long pendingChainEndTime() {
            long endTime = 0;
            for (PendingChain chain : pendingChains) {
                endTime = max(endTime, chain.endTime);
            }
            return endTime;
        }
    }

    private static class PendingChain {
        private final long endTime;
        private final long durationMillis;

        PendingChain(long endTime, long durationMillis) {
            this.endTime = endTime;
            this.durationMillis = durationMillis;
        }
    }

    private static class RunningWork {
        private final ScheduledWork scheduledWork;
        private final long startTime;
        private final long durationMillis;
        // Duration of the longest chain of work that runs after this work item
        private final long followingMillis;

        RunningWork(ScheduledWork scheduledWork, long startTime, long durationMillis, long followingMillis) {
            this.scheduledWork = scheduledWork;
            this.startTime = startTime;
            this.durationMillis = durationMillis;
            this.followingMillis = followingMillis;
        }
    }

    private static class WaitingWork {
        private final ScheduledWork scheduledWork;
        private final long followingMillis;
        private final ScheduledWork nestedWork;

        WaitingWork(ScheduledWork scheduledWork, long followingMillis, ScheduledWork nestedWork) {
            this.scheduledWork = scheduledWork;
            this.followingMillis = followingMillis;
            this.nestedWork = nestedWork;
        }
    }
}
