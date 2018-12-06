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
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.GradleThread;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.ParallelismConfigurationListener;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.time.Clock;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ObjectStreamException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

//TODO move to base-services once the ProgressLogger dependency is removed
public class DefaultBuildOperationExecutor implements BuildOperationExecutor, Stoppable, ParallelismConfigurationListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationExecutor.class);
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();

    private final BuildOperationListener listener;
    private final Clock clock;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final BuildOperationQueueFactory buildOperationQueueFactory;
    private final ManagedExecutor fixedSizePool;
    private final ParallelismConfigurationManager parallelismConfigurationManager;
    private final BuildOperationIdFactory buildOperationIdFactory;

    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();
    private final RunnableBuildOperationWorker runnableBuildOperationWorker = new RunnableBuildOperationWorker();

    public DefaultBuildOperationExecutor(BuildOperationListener listener, Clock clock, ProgressLoggerFactory progressLoggerFactory, BuildOperationQueueFactory buildOperationQueueFactory, ExecutorFactory executorFactory, ParallelismConfigurationManager parallelismConfigurationManager, BuildOperationIdFactory buildOperationIdFactory) {
        this.listener = listener;
        this.clock = clock;
        this.progressLoggerFactory = progressLoggerFactory;
        this.buildOperationQueueFactory = buildOperationQueueFactory;
        this.fixedSizePool = executorFactory.create("Build operations", parallelismConfigurationManager.getParallelismConfiguration().getMaxWorkerCount());
        this.parallelismConfigurationManager = parallelismConfigurationManager;
        this.buildOperationIdFactory = buildOperationIdFactory;
        parallelismConfigurationManager.addListener(this);
    }

    @Override
    public void onParallelismConfigurationChange(ParallelismConfiguration parallelismConfiguration) {
        fixedSizePool.setFixedPoolSize(parallelismConfiguration.getMaxWorkerCount());
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
    public void run(RunnableBuildOperation buildOperation) {
        try {
            execute(buildOperation, runnableBuildOperationWorker, getCurrentBuildOperation());
        } finally {
            maybeStopUnmanagedThreadOperation();
        }
    }

    @Override
    public <T> T call(CallableBuildOperation<T> buildOperation) {
        CallableBuildOperationWorker<T> worker = new CallableBuildOperationWorker<T>();
        try {
            execute(buildOperation, worker, getCurrentBuildOperation());
        } finally {
            maybeStopUnmanagedThreadOperation();
        }
        return worker.getReturnValue();
    }

    @Override
    public <O extends RunnableBuildOperation> void runAll(Action<BuildOperationQueue<O>> schedulingAction) {
        try {
            executeInParallel(new ParentPreservingQueueWorker<O>(runnableBuildOperationWorker), schedulingAction);
        } finally {
            maybeStopUnmanagedThreadOperation();
        }
    }

    @Override
    public <O extends BuildOperation> void runAll(BuildOperationWorker<O> worker, Action<BuildOperationQueue<O>> schedulingAction) {
        try {
            executeInParallel(new ParentPreservingQueueWorker<O>(worker), schedulingAction);
        } finally {
            maybeStopUnmanagedThreadOperation();
        }
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

    private <O extends BuildOperation> void execute(O buildOperation, BuildOperationWorker<O> worker, @Nullable BuildOperationState defaultParent) {
        BuildOperationDescriptor.Builder descriptorBuilder = buildOperation.description();
        BuildOperationState parent = (BuildOperationState) descriptorBuilder.getParentState();
        if (parent == null) {
            parent = defaultParent;
        }

        BuildOperationDescriptor descriptor = createDescriptor(descriptorBuilder, parent);
        BuildOperationState newOperation = new BuildOperationState(descriptor, clock.getCurrentTime());

        assertParentRunning("Cannot start operation (%s) as parent operation (%s) has already completed.", descriptor, parent);

        newOperation.setRunning(true);

        BuildOperationState parentOperation = getCurrentBuildOperation();
        setCurrentBuildOperation(newOperation);
        try {
            listener.started(descriptor, new OperationStartEvent(newOperation.getStartTime()));
            ProgressLogger progressLogger = createProgressLogger(newOperation);

            Throwable failure = null;
            DefaultBuildOperationContext context = new DefaultBuildOperationContext();

            LOGGER.debug("Build operation '{}' started", descriptor.getDisplayName());
            try {
                worker.execute(buildOperation, context);
            } catch (Throwable t) {
                context.thrown(t);
                failure = t;
            }
            LOGGER.debug("Completing Build operation '{}'", descriptor.getDisplayName());

            progressLogger.completed(context.status, context.failure != null);
            listener.finished(descriptor, new OperationFinishEvent(newOperation.getStartTime(), clock.getCurrentTime(), context.failure, context.result));

            assertParentRunning("Parent operation (%2$s) completed before this operation (%1$s).", descriptor, parent);

            if (failure != null) {
                throw UncheckedException.throwAsUncheckedException(failure, true);
            }

        } finally {
            setCurrentBuildOperation(parentOperation);
            newOperation.setRunning(false);
            LOGGER.debug("Build operation '{}' completed", descriptor.getDisplayName());
        }
    }

    private BuildOperationDescriptor createDescriptor(BuildOperationDescriptor.Builder descriptorBuilder, BuildOperationState parent) {
        OperationIdentifier id = new OperationIdentifier(buildOperationIdFactory.nextId());
        BuildOperationState current = maybeStartUnmanagedThreadOperation(parent);
        return descriptorBuilder.build(id, current == null ? null : current.getDescription().getId());
    }

    private void assertParentRunning(String message, BuildOperationDescriptor child, BuildOperationState parent) {
        if (parent != null && !parent.isRunning()) {
            String parentName = parent.getDescription().getDisplayName();
            throw new IllegalStateException(String.format(message, child.getDisplayName(), parentName));
        }
    }

    private ProgressLogger createProgressLogger(BuildOperationState currentOperation) {
        BuildOperationDescriptor descriptor = currentOperation.getDescription();
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationExecutor.class, descriptor);
        return progressLogger.start(descriptor.getDisplayName(), descriptor.getProgressDisplayName());
    }

    private BuildOperationState maybeStartUnmanagedThreadOperation(BuildOperationState parentState) {
        if (parentState == null && !GradleThread.isManaged()) {
            parentState = UnmanagedThreadOperation.create(clock);
            parentState.setRunning(true);
            setCurrentBuildOperation(parentState);
            listener.started(parentState.getDescription(), new OperationStartEvent(parentState.getStartTime()));
        }
        return parentState;
    }

    private void maybeStopUnmanagedThreadOperation() {
        BuildOperationState current = getCurrentBuildOperation();
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
        return StringUtils.join(CollectionUtils.collect(failures, new Transformer<String, GradleException>() {
            @Override
            public String transform(GradleException e) {
                return e.getMessage();
            }
        }), LINE_SEPARATOR + "AND" + LINE_SEPARATOR);
    }

    @Override
    public void stop() {
        parallelismConfigurationManager.removeListener(this);
        fixedSizePool.stop();
    }

    private static class DefaultBuildOperationContext implements BuildOperationContext {
        Throwable failure;
        Object result;
        private String status;

        @Override
        public void failed(Throwable t) {
            failure = t;
        }

        public void thrown(Throwable t) {
            if (failure == null) {
                failure = t;
            }
        }

        @Override
        public void setResult(Object result) {
            this.result = result;
        }

        @Override
        public void setStatus(String status) {
            this.status = status;
        }
    }

    private static class RunnableBuildOperationWorker implements BuildOperationWorker<RunnableBuildOperation> {
        @Override
        public String getDisplayName() {
            return "runnable build operation";
        }

        @Override
        public void execute(RunnableBuildOperation buildOperation, BuildOperationContext context) {
            buildOperation.run(context);
        }
    }

    private static class CallableBuildOperationWorker<T> implements BuildOperationWorker<CallableBuildOperation<T>> {
        private T returnValue;

        @Override
        public String getDisplayName() {
            return "callable build operation";
        }

        @Override
        public void execute(CallableBuildOperation<T> buildOperation, BuildOperationContext context) {
            returnValue = buildOperation.call(context);
        }

        public T getReturnValue() {
            return returnValue;
        }
    }

    /**
     * Remembers the operation running on the executing thread at creation time to use
     * it during execution on other threads.
     */
    private class ParentPreservingQueueWorker<O extends BuildOperation> implements BuildOperationQueue.QueueWorker<O> {
        private BuildOperationState parent;
        private BuildOperationWorker<? super O> worker;

        private ParentPreservingQueueWorker(BuildOperationWorker<? super O> worker) {
            this.parent = maybeStartUnmanagedThreadOperation(getCurrentBuildOperation());
            this.worker = worker;
        }

        @Override
        public String getDisplayName() {
            return "runnable worker";
        }

        @Override
        public void execute(O buildOperation) {
            DefaultBuildOperationExecutor.this.execute(buildOperation, worker, parent);
        }
    }

    private void setCurrentBuildOperation(BuildOperationState parentState) {
        currentBuildOperationRef.set(parentState);
    }

    private BuildOperationState getCurrentBuildOperation() {
        return (BuildOperationState) currentBuildOperationRef.get();
    }

    private static class BuildOperationState implements BuildOperationRef {
        private final BuildOperationDescriptor description;
        private final AtomicBoolean running = new AtomicBoolean();
        private final long startTime;

        private BuildOperationState(BuildOperationDescriptor descriptor, long startTime) {
            this.startTime = startTime;
            this.description = descriptor;
        }

        BuildOperationDescriptor getDescription() {
            return description;
        }

        boolean isRunning() {
            return running.get();
        }

        void setRunning(boolean running) {
            this.running.set(running);
        }

        long getStartTime() {
            return startTime;
        }

        @Override
        public OperationIdentifier getId() {
            return description.getId();
        }

        @Override
        public OperationIdentifier getParentId() {
            return description.getParentId();
        }

        private Object writeReplace() throws ObjectStreamException {
            return new DefaultBuildOperationRef(description.getId(), description.getParentId());
        }
    }

    private static class UnmanagedThreadOperation extends BuildOperationState {

        private static final AtomicLong UNMANAGED_THREAD_OPERATION_COUNTER = new AtomicLong(-1);

        private static UnmanagedThreadOperation create(Clock clock) {
            // TODO:pm Move this to WARN level once we fixed maven-publish, see gradle/gradle#1662
            LOGGER.debug("WARNING No operation is currently running in unmanaged thread: {}", Thread.currentThread().getName());
            OperationIdentifier id = new OperationIdentifier(UNMANAGED_THREAD_OPERATION_COUNTER.getAndDecrement());
            String displayName = "Unmanaged thread operation #" + id + " (" + Thread.currentThread().getName() + ')';
            return new UnmanagedThreadOperation(BuildOperationDescriptor.displayName(displayName).build(id, null), null, clock.getCurrentTime());
        }

        private UnmanagedThreadOperation(BuildOperationDescriptor descriptor, BuildOperationRef parent, long startTime) {
            super(descriptor, startTime);
        }
    }
}
