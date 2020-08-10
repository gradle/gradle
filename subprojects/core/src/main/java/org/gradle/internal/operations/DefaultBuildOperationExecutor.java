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
import org.gradle.internal.concurrent.GradleThread;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@ServiceScope(Scopes.BuildSession.class)
public class DefaultBuildOperationExecutor implements BuildOperationExecutor, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationExecutor.class);
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();

    private final DefaultBuildOperationRunner runner;
    private final BuildOperationListener listener;
    private final Clock clock;
    private final BuildOperationQueueFactory buildOperationQueueFactory;
    private final ManagedExecutor fixedSizePool;
    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();

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
            clock::getCurrentTime,
            buildOperationIdFactory,
            (delegate) -> new ListenerAdapter(listener, progressLoggerFactory, clock, delegate)
        );
        this.listener = listener;
        this.clock = clock;
        this.buildOperationQueueFactory = buildOperationQueueFactory;
        this.fixedSizePool = executorFactory.create("Build operations", parallelismConfiguration.getMaxWorkerCount());
    }

    @Override
    public void run(RunnableBuildOperation buildOperation) {
        BuildOperationState parent = maybeStartUnmanagedThreadOperation();
        try {
            runner.run(buildOperation);
        } finally {
            maybeStopUnmanagedThreadOperation(parent);
        }
    }

    @Override
    public <T> T call(CallableBuildOperation<T> buildOperation) {
        BuildOperationState parent = maybeStartUnmanagedThreadOperation();
        try {
            return runner.call(buildOperation);
        } finally {
            maybeStopUnmanagedThreadOperation(parent);
        }
    }

    @Override
    public BuildOperationContext start(BuildOperationDescriptor.Builder descriptor) {
        return runner.start(descriptor);
    }

    @Override
    public BuildOperationRef getCurrentOperation() {
        return runner.getCurrentOperation();
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        BuildOperationState parent = maybeStartUnmanagedThreadOperation();
        try {
            executeInParallel(new QueueWorker<>(parent, RunnableBuildOperation::run), schedulingAction);
        } finally {
            maybeStopUnmanagedThreadOperation(parent);
        }
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        BuildOperationState parent = maybeStartUnmanagedThreadOperation();
        try {
            executeInParallel(new QueueWorker<>(parent, worker), schedulingAction);
        } finally {
            maybeStopUnmanagedThreadOperation(parent);
        }
    }

    @Nullable
    private BuildOperationState getCurrentBuildOperation() {
        return (BuildOperationState) currentBuildOperationRef.get();
    }

    private void setCurrentBuildOperation(@Nullable BuildOperationState parentState) {
        currentBuildOperationRef.set(parentState);
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

    @Nullable
    private BuildOperationState maybeStartUnmanagedThreadOperation() {
        BuildOperationState current = getCurrentBuildOperation();
        if (current == null && !GradleThread.isManaged()) {
            BuildOperationState unmanaged = UnmanagedThreadOperation.create(clock.getCurrentTime());
            unmanaged.setRunning(true);
            setCurrentBuildOperation(unmanaged);
            listener.started(unmanaged.getDescription(), new OperationStartEvent(unmanaged.getStartTime()));
            return unmanaged;
        } else {
            return current;
        }
    }

    private void maybeStopUnmanagedThreadOperation(@Nullable BuildOperationState current) {
        if (current instanceof UnmanagedThreadOperation) {
            try {
                listener.finished(current.getDescription(), new OperationFinishEvent(current.getStartTime(), clock.getCurrentTime(), null, null));
            } finally {
                setCurrentBuildOperation(null);
                current.setRunning(false);
            }
        }
    }

    /**
     * Artificially create a running root operation.
     * Main use case is ProjectBuilder, useful for some of our test fixtures too.
     */
    protected void createRunningRootOperation(String displayName) {
        assert getCurrentBuildOperation() == null;
        OperationIdentifier rootBuildOpId = new OperationIdentifier(DefaultBuildOperationIdFactory.ROOT_BUILD_OPERATION_ID_VALUE);
        BuildOperationState operation = new BuildOperationState(BuildOperationDescriptor.displayName(displayName).build(rootBuildOpId, null), clock.getCurrentTime());
        operation.setRunning(true);
        setCurrentBuildOperation(operation);
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

    private static class ListenerAdapter implements BuildOperationExecutionListener {
        private final BuildOperationListener buildOperationListener;
        private final ProgressLoggerFactory progressLoggerFactory;
        private final Clock clock;
        private final BuildOperationExecutionListener delegate;
        private ProgressLogger progressLogger;

        public ListenerAdapter(BuildOperationListener buildOperationListener, ProgressLoggerFactory progressLoggerFactory, Clock clock, BuildOperationExecutionListener delegate) {
            this.buildOperationListener = buildOperationListener;
            this.progressLoggerFactory = progressLoggerFactory;
            this.clock = clock;
            this.delegate = delegate;
        }

        @Override
        public void start(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            delegate.start(descriptor, operationState);
            buildOperationListener.started(descriptor, new OperationStartEvent(operationState.getStartTime()));
            ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationExecutor.class, descriptor);
            this.progressLogger = progressLogger.start(descriptor.getDisplayName(), descriptor.getProgressDisplayName());
        }

        @Override
        public void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationContext context) {
            progressLogger.completed(context.status, context.failure != null);
            buildOperationListener.finished(descriptor, new OperationFinishEvent(operationState.getStartTime(), clock.getCurrentTime(), context.failure, context.result));
            delegate.stop(descriptor, operationState, parent, context);
        }

        @Override
        public void close(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            delegate.close(descriptor, operationState);
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

    private static class UnmanagedThreadOperation extends BuildOperationState {

        private static final AtomicLong UNMANAGED_THREAD_OPERATION_COUNTER = new AtomicLong(-1);

        private static UnmanagedThreadOperation create(long currentTime) {
            // TODO:pm Move this to WARN level once we fixed maven-publish, see gradle/gradle#1662
            LOGGER.debug("WARNING No operation is currently running in unmanaged thread: {}", Thread.currentThread().getName());
            OperationIdentifier id = new OperationIdentifier(UNMANAGED_THREAD_OPERATION_COUNTER.getAndDecrement());
            String displayName = "Unmanaged thread operation #" + id + " (" + Thread.currentThread().getName() + ')';
            return new UnmanagedThreadOperation(BuildOperationDescriptor.displayName(displayName).build(id, null), currentTime);
        }

        private UnmanagedThreadOperation(BuildOperationDescriptor descriptor, long startTime) {
            super(descriptor, startTime);
        }
    }
}
