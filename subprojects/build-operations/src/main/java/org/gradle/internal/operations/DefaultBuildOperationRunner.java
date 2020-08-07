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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public class DefaultBuildOperationRunner implements BuildOperationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationRunner.class);
    protected static final BuildOperationWorker<RunnableBuildOperation> RUNNABLE_BUILD_OPERATION_WORKER = new RunnableBuildOperationWorker();

    private final TimeSupplier clock;
    private final BuildOperationIdFactory buildOperationIdFactory;
    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();
    private final BuildOperationExecutionListener listener;

    public DefaultBuildOperationRunner(TimeSupplier clock, BuildOperationIdFactory buildOperationIdFactory, BuildOperationExecutionListener listener) {
        this.clock = clock;
        this.buildOperationIdFactory = buildOperationIdFactory;
        this.listener = new CompositeBuildOperationExecutionListener(Arrays.asList(
            new BuildOperationTrackingListener(currentBuildOperationRef),
            listener
        ));
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

    protected <O extends BuildOperation> void execute(final O buildOperation, final BuildOperationWorker<O> worker, @Nullable BuildOperationState defaultParent) {
        execute(buildOperation.description(), defaultParent, new BuildOperationExecution<O>() {
            @Override
            public O execute(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationContext context, BuildOperationExecutionListener listener) {
                Throwable failure = null;
                try {
                    listener.start(descriptor, operationState);
                    try {
                        worker.execute(buildOperation, context);
                    } catch (Throwable t) {
                        context.thrown(t);
                        failure = t;
                    }
                    listener.stop(descriptor, operationState, parent, context);
                    if (failure != null) {
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
            public BuildOperationContext execute(final BuildOperationDescriptor descriptor, final BuildOperationState operationState, @Nullable final BuildOperationState parent, final DefaultBuildOperationContext context, final BuildOperationExecutionListener listener) {
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
                    public void setResult(Object result) {
                        assertNotFinished();
                        context.setResult(result);
                        finish();
                    }

                    @Override
                    public void setStatus(String status) {
                        assertNotFinished();
                        context.setStatus(status);
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
        DefaultBuildOperationContext context = new DefaultBuildOperationContext();
        return execution.execute(descriptor, operationState, parent, context, listener);
    }

    private static void assertParentRunning(String message, BuildOperationDescriptor child, @Nullable BuildOperationState parent) {
        if (parent != null && !parent.isRunning()) {
            String parentName = parent.getDescription().getDisplayName();
            throw new IllegalStateException(String.format(message, child.getDisplayName(), parentName));
        }
    }

    @Override
    public BuildOperationRef getCurrentOperation() {
        BuildOperationRef current = getCurrentBuildOperation();
        if (current == null) {
            throw new IllegalStateException("No operation is currently running.");
        }
        return current;
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
        O execute(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationContext context, BuildOperationExecutionListener listener);
    }

    protected interface BuildOperationExecutionListener {
        void start(BuildOperationDescriptor descriptor, BuildOperationState operationState);

        void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationContext context);

        void close(BuildOperationDescriptor descriptor, BuildOperationState operationState);
    }

    protected static class DefaultBuildOperationContext implements BuildOperationContext {
        Throwable failure;
        Object result;
        protected String status;

        @Override
        public void failed(@Nullable Throwable t) {
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
        private RunnableBuildOperationWorker() {}

        @Override
        public String getDisplayName() {
            return "runnable build operation";
        }

        @Override
        public void execute(RunnableBuildOperation buildOperation, BuildOperationContext context) throws Exception {
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
        public void execute(CallableBuildOperation<T> buildOperation, BuildOperationContext context) throws Exception {
            returnValue = buildOperation.call(context);
        }

        public T getReturnValue() {
            return returnValue;
        }
    }

    private static class CompositeBuildOperationExecutionListener implements BuildOperationExecutionListener {
        private final List<BuildOperationExecutionListener> listeners;

        public CompositeBuildOperationExecutionListener(List<BuildOperationExecutionListener> listeners) {
            this.listeners = listeners;
        }

        @Override
        public void start(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            for (BuildOperationExecutionListener listener : listeners) {
                listener.start(descriptor, operationState);
            }
        }

        @Override
        public void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationContext context) {
            for (BuildOperationExecutionListener listener : listeners) {
                listener.stop(descriptor, operationState, parent, context);
            }
        }

        @Override
        public void close(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            for (BuildOperationExecutionListener listener : listeners) {
                listener.close(descriptor, operationState);
            }
        }
    }

    private static class BuildOperationTrackingListener implements BuildOperationExecutionListener {
        private final CurrentBuildOperationRef currentBuildOperationRef;
        private BuildOperationState originalCurrentBuildOperation;

        private BuildOperationTrackingListener(CurrentBuildOperationRef currentBuildOperationRef) {
            this.currentBuildOperationRef = currentBuildOperationRef;
        }

        @Override
        public void start(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            originalCurrentBuildOperation = (BuildOperationState) currentBuildOperationRef.get();
            currentBuildOperationRef.set(operationState);
            operationState.setRunning(true);
            LOGGER.debug("Build operation '{}' started", descriptor.getDisplayName());
        }

        @Override
        public void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationContext context) {
            LOGGER.debug("Completing Build operation '{}'", descriptor.getDisplayName());
            assertParentRunning("Parent operation (%2$s) completed before this operation (%1$s).", descriptor, parent);
        }

        @Override
        public void close(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
            currentBuildOperationRef.set(originalCurrentBuildOperation);
            operationState.setRunning(false);
            LOGGER.debug("Build operation '{}' completed", descriptor.getDisplayName());
        }
    }
}
