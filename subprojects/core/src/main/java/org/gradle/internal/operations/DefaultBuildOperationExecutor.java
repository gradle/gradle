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

package org.gradle.internal.operations;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.work.SubmissionQueue;
import org.gradle.internal.work.WorkerLeaseQueueExecutor;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.internal.work.WorkerLimits;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

public class DefaultBuildOperationExecutor implements BuildOperationExecutor, Stoppable {
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();

    private final BuildOperationRunner runner;
    private final BuildOperationQueueFactory buildOperationQueueFactory;
    private final CurrentBuildOperationRef currentBuildOperationRef;

    private final WorkerLeaseQueueExecutor maxWorkersExecutor;
    private final ManagedExecutor maxWorkersBackingExecutor;
    private final ManagedExecutor unconstrainedExecutor;

    public DefaultBuildOperationExecutor(
        BuildOperationRunner buildOperationRunner,
        CurrentBuildOperationRef currentBuildOperationRef,
        BuildOperationQueueFactory buildOperationQueueFactory,
        ExecutorFactory executorFactory,
        ResourceLockCoordinationService coordinationService,
        WorkerLeaseService workerLeaseService,
        WorkerLimits workerLimits
    ) {
        this.runner = buildOperationRunner;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.buildOperationQueueFactory = buildOperationQueueFactory;

        // -1 because waitForCompletion thread executes work as well. See https://github.com/gradle/gradle/issues/3273
        // Floor at 1 so single-worker builds still have at least one backing thread; WorkerCounter would refuse to spawn
        // any worker if core==0, which would force every op onto the submitter's drain pass.
        int maxWorkersWithoutCurrentThread = Math.max(1, workerLimits.getMaxWorkerCount() - 1);
        this.maxWorkersBackingExecutor = executorFactory.create(
            "Build operations",
            workerLimits.getMaxWorkerCount()
        );
        this.maxWorkersExecutor = new WorkerLeaseQueueExecutor(
            coordinationService,
            workerLeaseService,
            maxWorkersBackingExecutor,
            maxWorkersWithoutCurrentThread,
            workerLimits.getMaxUnconstrainedWorkerCount()
        );
        this.unconstrainedExecutor = executorFactory.create(
            "Unconstrained build operations",
            workerLimits.getMaxUnconstrainedWorkerCount()
        );
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        runAll(schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        executeInParallel(false, RunnableBuildOperation::run, schedulingAction, buildOperationConstraint);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction) {
        runAllWithAccessToProjectState(schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        executeInParallel(true, RunnableBuildOperation::run, schedulingAction, buildOperationConstraint);
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        runAll(worker, schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        executeInParallel(false, worker, schedulingAction, buildOperationConstraint);
    }

    private <O extends BuildOperation> void executeInParallel(
        boolean allowAccessToProjectState,
        BuildOperationWorker<O> worker,
        Action<BuildOperationQueue<O>> queueAction,
        BuildOperationConstraint buildOperationConstraint
    ) {
        SubmissionQueue submissionQueue = getSubmissionQueueFor(buildOperationConstraint);
        BuildOperationQueue<O> queue = buildOperationQueueFactory.create(
            submissionQueue,
            allowAccessToProjectState,
            operation -> runner.execute(operation, worker),
            currentBuildOperationRef.get()
        );

        List<GradleException> failures = new ArrayList<>();
        try {
            queueAction.execute(queue);
        } catch (Exception e) {
            failures.add(new BuildOperationQueueFailure("There was a failure while populating the build operation queue: " + e.getMessage(), e));
            queue.cancel();
        }

        try {
            queue.waitForCompletion();
        } catch (MultipleBuildOperationFailures e) {
            failures.add(e);
        }

        if (failures.size() == 1) {
            throw failures.get(0);
        } else if (failures.size() > 1) {
            throw new DefaultMultiCauseException(formatMultipleFailureMessage(failures), failures);
        }
    }

    private SubmissionQueue getSubmissionQueueFor(BuildOperationConstraint buildOperationConstraint) {
        switch (buildOperationConstraint) {
            case UNCONSTRAINED: return new UnconstrainedSubmissionQueue();
            case MAX_WORKERS: return maxWorkersExecutor.createSubmissionQueue();
            default: throw new IllegalArgumentException("Unknown build operation constraint: " + buildOperationConstraint);
        }
    }

    @NullMarked
    private class UnconstrainedSubmissionQueue implements SubmissionQueue {
        @Override
        public void add(Runnable task) {
            unconstrainedExecutor.execute(task);
        }

        @Override
        public void processWorkUsingCurrentThreadUntilEmptyOr(BooleanSupplier stoppingCondition) {
            // No-op, we're not limited by leases.
        }
    }

    private static String formatMultipleFailureMessage(List<GradleException> failures) {
        return failures.stream()
            .map(Throwable::getMessage)
            .collect(Collectors.joining(LINE_SEPARATOR + "AND" + LINE_SEPARATOR));
    }

    @Override
    public void stop() {
        // Shut down the lease-aware service first so it stops accepting new work; then stop the
        // backing pools, which waits for in-flight work to drain.
        maxWorkersExecutor.shutdown();
        CompositeStoppable.stoppable(maxWorkersBackingExecutor, unconstrainedExecutor).stop();
    }
}
