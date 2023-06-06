/*
 * Copyright 2020 the original author or authors.
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

import org.gradle.api.problems.Problems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

public class DefaultBuildOperationRunner implements BuildOperationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationRunner.class);
    private static final BuildOperationWorker<RunnableBuildOperation> RUNNABLE_BUILD_OPERATION_WORKER = new BuildOperationWorker<RunnableBuildOperation>() {
        @Override
        public void execute(RunnableBuildOperation buildOperation, BuildOperationContext context) throws Exception {
            buildOperation.run(context);
        }
    };

    private final TimeSupplier clock;
    private final BuildOperationIdFactory buildOperationIdFactory;
    private final CurrentBuildOperationRef currentBuildOperationRef;
    private final BuildOperationExecutionListenerFactory listenerFactory;

    public DefaultBuildOperationRunner(CurrentBuildOperationRef currentBuildOperationRef, TimeSupplier clock, BuildOperationIdFactory buildOperationIdFactory, BuildOperationExecutionListenerFactory listenerFactory) {
        this.currentBuildOperationRef = currentBuildOperationRef;
        this.clock = clock;
        this.buildOperationIdFactory = buildOperationIdFactory;
        this.listenerFactory = listenerFactory;
    }

    @Override
    public void run(RunnableBuildOperation buildOperation) {
        execute(buildOperation, RUNNABLE_BUILD_OPERATION_WORKER, getCurrentBuildOperation());
    }

    @Override
    public <T> T call(CallableBuildOperation<T> buildOperation) {
        CallableBuildOperationWorker<T> worker = new CallableBuildOperationWorker<T>();
        execute(buildOperation, worker, getCurrentBuildOperation());
        return worker.getReturnValue();
    }

    @Override
    public <O extends BuildOperation> void execute(final O buildOperation, final BuildOperationWorker<O> worker, @Nullable BuildOperationState defaultParent) {
        execute(buildOperation.description(), defaultParent, new BuildOperationExecution<O>() {
            @Override
            public O execute(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context, BuildOperationExecutionListener listener) {
                try {
                    listener.start(descriptor, operationState);
                    Throwable failure = null;
                    try {
                        worker.execute(buildOperation, context);
                    } catch (Throwable t) {
                        if (context.getFailure() == null) {
                            context.failed(t);
                        }
                        failure = t;
                    }
                    listener.stop(descriptor, operationState, parent, context);
                    if (failure != null) {
                        Problems.collect(failure);
                        throw throwAsBuildOperationInvocationException(failure);
                    }
                    return buildOperation;
                } finally {
                    listener.close(descriptor, operationState);
                }
            }
        });
    }

    @Override
    public BuildOperationContext start(BuildOperationDescriptor.Builder descriptorBuilder) {
        return execute(descriptorBuilder, getCurrentBuildOperation(), new BuildOperationExecution<BuildOperationContext>() {
            @Override
            public BuildOperationContext execute(final BuildOperationDescriptor descriptor, final BuildOperationState operationState, @Nullable final BuildOperationState parent, final ReadableBuildOperationContext context, final BuildOperationExecutionListener listener) {
                listener.start(descriptor, operationState);
                return new BuildOperationContext() {
                    private boolean finished;

                    @Override
                    public void failed(@Nullable Throwable failure) {
                        assertNotFinished();
                        context.failed(failure);
                        finish();
                    }

                    @Override
                    public void setResult(@Nullable Object result) {
                        assertNotFinished();
                        context.setResult(result);
                        finish();
                    }

                    @Override
                    public void setStatus(String status) {
                        assertNotFinished();
                        context.setStatus(status);
                    }

                    @Override
                    public void progress(String status) {
                        assertNotFinished();
                        context.progress(status);
                    }

                    @Override
                    public void progress(long progress, long total, String units, String status) {
                        assertNotFinished();
                        context.progress(progress, total, units, status);
                    }

                    private void finish() {
                        finished = true;
                        try {
                            listener.stop(descriptor, operationState, parent, context);
                        } finally {
                            listener.close(descriptor, operationState);
                        }
                    }

                    private void assertNotFinished() {
                        if (finished) {
                            throw new IllegalStateException(String.format("Operation (%s) has already finished.", descriptor));
                        }
                    }
                };
            }
        });
    }

    private <O> O execute(BuildOperationDescriptor.Builder descriptorBuilder, @Nullable BuildOperationState defaultParent, BuildOperationExecution<O> execution) {
        BuildOperationState descriptorParent = (BuildOperationState) descriptorBuilder.getParentState();
        BuildOperationState parent = descriptorParent == null ? defaultParent : descriptorParent;
        OperationIdentifier id = new OperationIdentifier(buildOperationIdFactory.nextId());
        BuildOperationDescriptor descriptor = descriptorBuilder.build(id, parent == null
            ? null
            : parent.getDescription().getId());
        assertParentRunning("Cannot start operation (%s) as parent operation (%s) has already completed.", descriptor, parent);

        BuildOperationState operationState = new BuildOperationState(descriptor, clock.getCurrentTime());
        BuildOperationTrackingListener listener = new BuildOperationTrackingListener(currentBuildOperationRef, listenerFactory.createListener());
        DefaultBuildOperationContext context = new DefaultBuildOperationContext(descriptor, listener);
        return execution.execute(
            descriptor,
            operationState,
            parent,
            context,
            listener
        );
    }

    private static void assertParentRunning(String message, BuildOperationDescriptor child, @Nullable BuildOperationState parent) {
        if (parent != null && !parent.isRunning()) {
            String parentName = parent.getDescription().getDisplayName();
            throw new IllegalStateException(String.format(message, child.getDisplayName(), parentName));
        }
    }

    @Nullable
    private BuildOperationState getCurrentBuildOperation() {
        return (BuildOperationState) currentBuildOperationRef.get();
    }

    public interface TimeSupplier {
        long getCurrentTime();
    }

    private static RuntimeException throwAsBuildOperationInvocationException(Throwable t) {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        throw new BuildOperationInvocationException(t.getMessage(), t);
    }

    private interface BuildOperationExecution<O> {
        O execute(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context, BuildOperationExecutionListener listener);
    }

    private static class CallableBuildOperationWorker<T> implements BuildOperationWorker<CallableBuildOperation<T>> {
        private T returnValue;

        @Override
        public void execute(CallableBuildOperation<T> buildOperation, BuildOperationContext context) throws Exception {
            returnValue = buildOperation.call(context);
        }

        public T getReturnValue() {
            return returnValue;
        }
    }

    private static class BuildOperationTrackingListener implements BuildOperationExecutionListener {
        private final CurrentBuildOperationRef currentBuildOperationRef;
        private final BuildOperationExecutionListener delegate;
        private BuildOperationState originalCurrentBuildOperation;

        private BuildOperationTrackingListener(CurrentBuildOperationRef currentBuildOperationRef, BuildOperationExecutionListener delegate) {
            this.currentBuildOperationRef = currentBuildOperationRef;
            this.delegate = delegate;
        }

        @Override
        public void start(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            originalCurrentBuildOperation = (BuildOperationState) currentBuildOperationRef.get();
            currentBuildOperationRef.set(operationState);
            operationState.setRunning(true);
            LOGGER.debug("Build operation '{}' started", descriptor.getDisplayName());
            delegate.start(descriptor, operationState);
        }

        @Override
        public void progress(BuildOperationDescriptor descriptor, String status) {
            delegate.progress(descriptor, status);
        }

        @Override
        public void progress(BuildOperationDescriptor descriptor, long progress, long total, String units, String status) {
            delegate.progress(descriptor, progress, total, units, status);
        }

        @Override
        public void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context) {
            delegate.stop(descriptor, operationState, parent, context);
            LOGGER.debug("Completing Build operation '{}'", descriptor.getDisplayName());
            assertParentRunning("Parent operation (%2$s) completed before this operation (%1$s).", descriptor, parent);
        }

        @Override
        public void close(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            delegate.close(descriptor, operationState);
            currentBuildOperationRef.set(originalCurrentBuildOperation);
            operationState.setRunning(false);
            LOGGER.debug("Build operation '{}' completed", descriptor.getDisplayName());
        }
    }

    public interface ReadableBuildOperationContext extends BuildOperationContext {
        @Nullable
        Object getResult();

        @Override
        void setResult(@Nullable Object result);

        @Nullable
        Throwable getFailure();

        @Nullable
        String getStatus();

        @Override
        void setStatus(@Nullable String status);
    }

    public interface BuildOperationExecutionListener {
        BuildOperationExecutionListener NO_OP = new BuildOperationExecutionListener() {
            @Override
            public void start(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            }

            @Override
            public void progress(BuildOperationDescriptor descriptor, String status) {
            }

            @Override
            public void progress(BuildOperationDescriptor descriptor, long progress, long total, String units, String status) {
            }

            @Override
            public void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context) {
            }

            @Override
            public void close(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            }
        };

        void start(BuildOperationDescriptor descriptor, BuildOperationState operationState);

        void progress(BuildOperationDescriptor descriptor, String status);

        void progress(BuildOperationDescriptor descriptor, long progress, long total, String units, String status);

        void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, ReadableBuildOperationContext context);

        void close(BuildOperationDescriptor descriptor, BuildOperationState operationState);
    }

    public interface BuildOperationExecutionListenerFactory {
        BuildOperationExecutionListener createListener();
    }

    private static class DefaultBuildOperationContext implements ReadableBuildOperationContext {
        private final BuildOperationDescriptor descriptor;
        private final BuildOperationExecutionListener listener;
        private Throwable failure;
        private Object result;
        private String status;

        public DefaultBuildOperationContext(BuildOperationDescriptor descriptor, BuildOperationExecutionListener listener) {
            this.descriptor = descriptor;
            this.listener = listener;
        }

        @Nullable
        @Override
        public Object getResult() {
            return result;
        }

        @Override
        public void setResult(@Nullable Object result) {
            this.result = result;
        }

        @Nullable
        @Override
        public Throwable getFailure() {
            return failure;
        }

        @Override
        public void failed(@Nullable Throwable t) {
            failure = t;
        }

        @Nullable
        @Override
        public String getStatus() {
            return status;
        }

        @Override
        public void setStatus(@Nullable String status) {
            this.status = status;
        }

        @Override
        public void progress(String status) {
            listener.progress(descriptor, status);
        }

        @Override
        public void progress(long progress, long total, String units, String status) {
            listener.progress(descriptor, progress, total, units, status);
        }
    }
}
