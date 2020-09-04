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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.time.Clock;

import javax.annotation.Nullable;
import java.util.List;
import java.util.stream.Collectors;

public class DefaultBuildOperationExecutor implements BuildOperationExecutor, Stoppable {
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();

    private final BuildOperationRunner runner;
    private final BuildOperationQueueFactory buildOperationQueueFactory;
    private final ManagedExecutor fixedSizePool;
    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();
    private final UnmanagedBuildOperationWrapper wrapper;

    public DefaultBuildOperationExecutor(
        BuildOperationListener listener,
        Clock clock,
        ProgressLoggerFactory progressLoggerFactory,
        BuildOperationQueueFactory buildOperationQueueFactory,
        ExecutorFactory executorFactory,
        ParallelismConfiguration parallelismConfiguration,
        BuildOperationIdFactory buildOperationIdFactory
    ) {
        this.runner = new DefaultBuildOperationRunner(
            currentBuildOperationRef,
            clock::getCurrentTime,
            buildOperationIdFactory,
            () -> new ListenerAdapter(listener, progressLoggerFactory, clock)
        );
        this.wrapper = new UnmanagedBuildOperationWrapper(
            listener,
            clock,
            currentBuildOperationRef
        );
        this.buildOperationQueueFactory = buildOperationQueueFactory;
        this.fixedSizePool = executorFactory.create("Build operations", parallelismConfiguration.getMaxWorkerCount());
    }

    @Override
    public void run(RunnableBuildOperation buildOperation) {
        wrapper.runWithUnmanagedSupport(getCurrentBuildOperation(), parent -> runner.run(buildOperation));
    }

    @Override
    public <T> T call(CallableBuildOperation<T> buildOperation) {
        return wrapper.callWithUnmanagedSupport(getCurrentBuildOperation(), parent -> runner.call(buildOperation));
    }

    @Override
    public <O extends BuildOperation> void execute(O buildOperation, BuildOperationWorker<O> worker, @Nullable BuildOperationState defaultParent) {
        wrapper.runWithUnmanagedSupport(defaultParent, parent -> runner.execute(buildOperation, worker, parent));
    }

    @Override
    public BuildOperationContext start(BuildOperationDescriptor.Builder descriptor) {
        return runner.start(descriptor);
    }

    @Override
    public BuildOperationRef getCurrentOperation() {
        BuildOperationRef current = getCurrentBuildOperation();
        if (current == null) {
            throw new IllegalStateException("No operation is currently running.");
        }
        return current;
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        wrapper.runWithUnmanagedSupport(getCurrentBuildOperation(), parent -> executeInParallel(new QueueWorker<>(parent, RunnableBuildOperation::run), schedulingAction));
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        wrapper.runWithUnmanagedSupport(getCurrentBuildOperation(), parent -> executeInParallel(new QueueWorker<>(parent, worker), schedulingAction));
    }

    @Nullable
    private BuildOperationState getCurrentBuildOperation() {
        return (BuildOperationState) currentBuildOperationRef.get();
    }

    private <O extends BuildOperation> void executeInParallel(BuildOperationQueue.QueueWorker<O> worker, Action<BuildOperationQueue<O>> queueAction) {
        BuildOperationQueue<O> queue = buildOperationQueueFactory.create(fixedSizePool, worker);

        List<GradleException> failures = Lists.newArrayList();
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
        fixedSizePool.stop();
    }

    private static class ListenerAdapter implements DefaultBuildOperationRunner.BuildOperationExecutionListener {
        private final BuildOperationListener buildOperationListener;
        private final ProgressLoggerFactory progressLoggerFactory;
        private final Clock clock;
        private ProgressLogger progressLogger;

        public ListenerAdapter(BuildOperationListener buildOperationListener, ProgressLoggerFactory progressLoggerFactory, Clock clock) {
            this.buildOperationListener = buildOperationListener;
            this.progressLoggerFactory = progressLoggerFactory;
            this.clock = clock;
        }

        @Override
        public void start(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            buildOperationListener.started(descriptor, new OperationStartEvent(operationState.getStartTime()));
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationExecutor.class, descriptor);
            this.progressLogger = progressLogger.start(descriptor.getDisplayName(), descriptor.getProgressDisplayName());
        }

        @Override
        public void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationRunner.ReadableBuildOperationContext context) {
            progressLogger.completed(context.getStatus(), context.getFailure() != null);
            buildOperationListener.finished(descriptor, new OperationFinishEvent(operationState.getStartTime(), clock.getCurrentTime(), context.getFailure(), context.getResult()));
        }

        @Override
        public void close(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
        }
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
