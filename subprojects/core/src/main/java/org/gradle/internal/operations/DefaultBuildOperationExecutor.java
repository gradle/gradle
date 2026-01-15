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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.work.WorkerLimits;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultBuildOperationExecutor implements BuildOperationExecutor, Stoppable {

    /**
     * The minimum number of parallel operations permitted on the unconstrained executor.
     */
    // Chosen since this value is used by kotlinx coroutines for their own IO scheduler:
    // https://github.com/Kotlin/kotlinx.coroutines/blob/1f521941faad4d2ee9c8236a7d5fa2c62eaa6b7d/kotlinx-coroutines-core/jvm/src/scheduling/Dispatcher.kt#L67
    public static final int MIN_UNCONSTRAINED_EXECUTOR_PARALLELISM = 64;

    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();

    private final BuildOperationRunner runner;
    private final BuildOperationQueueFactory buildOperationQueueFactory;
    private final CurrentBuildOperationRef currentBuildOperationRef;

    private final BuildOperationExecutionContext maxWorkersExecutionContext;
    private final BuildOperationExecutionContext unconstrainedExecutionContext;

    public DefaultBuildOperationExecutor(
        BuildOperationRunner buildOperationRunner,
        CurrentBuildOperationRef currentBuildOperationRef,
        BuildOperationQueueFactory buildOperationQueueFactory,
        ExecutorFactory executorFactory,
        WorkerLimits workerLimits
    ) {
        this.runner = buildOperationRunner;
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.buildOperationQueueFactory = buildOperationQueueFactory;

        this.maxWorkersExecutionContext = new BuildOperationExecutionContext(
            executorFactory.create("Build operations", workerLimits.getMaxWorkerCount()),
            workerLimits.getMaxWorkerCount(),
            true
        );

        int unconstrainedExecutorParallelism = Math.max(MIN_UNCONSTRAINED_EXECUTOR_PARALLELISM, workerLimits.getMaxWorkerCount());
        this.unconstrainedExecutionContext = new BuildOperationExecutionContext(
            executorFactory.create("Unconstrained build operations", unconstrainedExecutorParallelism),
            unconstrainedExecutorParallelism,
            false // Unconstrained operations do not require a worker lease since they are not intended for CPU intensive work
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
        BuildOperationExecutionContext executionContext = getExecutionContextFor(buildOperationConstraint);
        BuildOperationQueue<O> queue = buildOperationQueueFactory.create(
            executionContext,
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

    private BuildOperationExecutionContext getExecutionContextFor(BuildOperationConstraint buildOperationConstraint) {
        switch (buildOperationConstraint) {
            case UNCONSTRAINED: return unconstrainedExecutionContext;
            case MAX_WORKERS: return maxWorkersExecutionContext;
            default: throw new IllegalArgumentException("Unknown build operation constraint: " + buildOperationConstraint);
        }
    }

    private static String formatMultipleFailureMessage(List<GradleException> failures) {
        return failures.stream()
            .map(Throwable::getMessage)
            .collect(Collectors.joining(LINE_SEPARATOR + "AND" + LINE_SEPARATOR));
    }

    @Override
    public void stop() {
        maxWorkersExecutionContext.getExecutor().stop();
        unconstrainedExecutionContext.getExecutor().stop();
    }

}
