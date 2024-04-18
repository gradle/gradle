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
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.WorkerLimits;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultBuildOperationExecutor implements BuildOperationExecutor, Stoppable {
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();

    private final BuildOperationRunner runner;
    private final BuildOperationQueueFactory buildOperationQueueFactory;
    private final Map<BuildOperationConstraint, ManagedExecutor> managedExecutors = new HashMap<>();
    private final CurrentBuildOperationRef currentBuildOperationRef;

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
        managedExecutors.put(BuildOperationConstraint.MAX_WORKERS, executorFactory.create("Build operations", workerLimits.getMaxWorkerCount()));
        managedExecutors.put(BuildOperationConstraint.UNCONSTRAINED, executorFactory.create("Unconstrained build operations", workerLimits.getMaxWorkerCount() * 10));
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        runAll(schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        executeInParallel(false, new QueueWorker<>(getCurrentBuildOperation(), RunnableBuildOperation::run), schedulingAction, buildOperationConstraint);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction) {
        runAllWithAccessToProjectState(schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends RunnableBuildOperation> void runAllWithAccessToProjectState(Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        executeInParallel(true, new QueueWorker<>(getCurrentBuildOperation(), RunnableBuildOperation::run), schedulingAction, buildOperationConstraint);
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        runAll(worker, schedulingAction, BuildOperationConstraint.MAX_WORKERS);
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction, BuildOperationConstraint buildOperationConstraint) {
        executeInParallel(false, new QueueWorker<>(getCurrentBuildOperation(), worker), schedulingAction, buildOperationConstraint);
    }

    @Nullable
    private BuildOperationState getCurrentBuildOperation() {
        return (BuildOperationState) currentBuildOperationRef.get();
    }

    private <O extends BuildOperation> void executeInParallel(boolean allowAccessToProjectState, BuildOperationQueue.QueueWorker<O> worker, Action<BuildOperationQueue<O>> queueAction, BuildOperationConstraint buildOperationConstraint) {
        ManagedExecutor executor = managedExecutors.get(buildOperationConstraint);
        BuildOperationQueue<O> queue = buildOperationQueueFactory.create(executor, allowAccessToProjectState, worker);

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

    private static String formatMultipleFailureMessage(List<GradleException> failures) {
        return failures.stream()
            .map(Throwable::getMessage)
            .collect(Collectors.joining(LINE_SEPARATOR + "AND" + LINE_SEPARATOR));
    }

    @Override
    public void stop() {
        for (ManagedExecutor pool : managedExecutors.values()) {
            pool.stop();
        }
    }

    @Deprecated
    @Override
    public BuildOperationRef getCurrentOperation() {
        DeprecationLogger.deprecateInternalApi("BuildOperationExecutor.getCurrentOperation()")
            .willBeRemovedInGradle9()
            .undocumented()
            .nagUser();
        BuildOperationRef operationRef = currentBuildOperationRef.get();
        if (operationRef == null) {
            throw new IllegalStateException("No operation is currently running.");
        }
        return operationRef;
    }

    private class QueueWorker<O extends BuildOperation> implements BuildOperationQueue.QueueWorker<O> {
        private final BuildOperationState parent;
        private final BuildOperationWorker<? super O> worker;

        private QueueWorker(@Nullable BuildOperationState parent, BuildOperationWorker<? super O> worker) {
            this.parent = parent;
            this.worker = worker;
        }

        @Override
        public String getDisplayName() {
            return "runnable worker";
        }

        @Override
        public void execute(O buildOperation) {
            runner.execute(buildOperation, worker, parent);
        }
    }
}
