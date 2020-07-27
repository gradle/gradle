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

import org.gradle.internal.UncheckedException;
import org.gradle.internal.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ObjectStreamException;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultBuildOperationRunner implements BuildOperationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationRunner.class);

    protected final BuildOperationListener listener;
    protected final Clock clock;
    protected final RunnableBuildOperationWorker runnableBuildOperationWorker = new RunnableBuildOperationWorker();
    protected final BuildOperationIdFactory buildOperationIdFactory;
    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();

    public DefaultBuildOperationRunner(BuildOperationListener listener, Clock clock, BuildOperationIdFactory buildOperationIdFactory) {
        this.listener = listener;
        this.clock = clock;
        this.buildOperationIdFactory = buildOperationIdFactory;
    }

    @Override
    public void run(RunnableBuildOperation buildOperation) {
        execute(buildOperation, runnableBuildOperationWorker, getCurrentBuildOperation());
    }

    @Override
    public <T> T call(CallableBuildOperation<T> buildOperation) {
        CallableBuildOperationWorker<T> worker = new CallableBuildOperationWorker<>();
        execute(buildOperation, worker, getCurrentBuildOperation());
        return worker.getReturnValue();
    }

    @Override
    public BuildOperationContext start(BuildOperationDescriptor.Builder descriptor) {
        return start(descriptor, getCurrentBuildOperation());
    }

    protected <O extends BuildOperation> void execute(O buildOperation, BuildOperationWorker<O> worker, @Nullable BuildOperationState defaultParent) {
        BuildOperationDescriptor.Builder descriptorBuilder = buildOperation.description();
        execute(descriptorBuilder, defaultParent, (descriptor, operationState, context, listener) -> {
            Throwable failure = null;
            try {
                listener.start(operationState);
                try {
                    worker.execute(buildOperation, context);
                } catch (Throwable t) {
                    context.thrown(t);
                    failure = t;
                }
                listener.stop(operationState, context);
                if (failure != null) {
                    throw UncheckedException.throwAsUncheckedException(failure, true);
                }
                return buildOperation;
            } finally {
                listener.close(operationState);
            }
        });
    }

    protected BuildOperationContext start(BuildOperationDescriptor.Builder descriptorBuilder, @Nullable BuildOperationState defaultParent) {
        return execute(descriptorBuilder, defaultParent, (BuildOperationExecution<BuildOperationContext>) (descriptor, operationState, context, listener) -> {
            listener.start(operationState);
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
                        listener.stop(operationState, context);
                    } finally {
                        listener.close(operationState);
                    }
                }

                private void assertNotFinished() {
                    if (finished) {
                        throw new IllegalStateException(String.format("Operation (%s) has already finished.", descriptor));
                    }
                }
            };
        });
    }

    private <O> O execute(BuildOperationDescriptor.Builder descriptorBuilder, @Nullable BuildOperationState defaultParent, BuildOperationExecution<O> execution) {
        BuildOperationState parent = DefaultBuildOperationRunner.determineParent(descriptorBuilder, defaultParent);
        BuildOperationDescriptor descriptor = createDescriptor(descriptorBuilder, parent);

        assertParentRunning("Cannot start operation (%s) as parent operation (%s) has already completed.", descriptor, parent);

        BuildOperationState operationState = new BuildOperationState(descriptor, clock.getCurrentTime());
        operationState.setRunning(true);
        BuildOperationState parentOperation = getCurrentBuildOperation();
        setCurrentBuildOperation(operationState);

        return execute(descriptor, operationState, execution, new BuildOperationExecutionListener() {
            @Override
            public void start(BuildOperationState operationState) {
                listener.started(descriptor, new OperationStartEvent(operationState.getStartTime()));
                LOGGER.debug("Build operation '{}' started", descriptor.getDisplayName());
            }

            @Override
            public void stop(BuildOperationState operationState, DefaultBuildOperationContext context) {
                LOGGER.debug("Completing Build operation '{}'", descriptor.getDisplayName());
                listener.finished(descriptor, new OperationFinishEvent(operationState.getStartTime(), clock.getCurrentTime(), context.failure, context.result));
                assertParentRunning("Parent operation (%2$s) completed before this operation (%1$s).", descriptor, parent);
            }

            @Override
            public void close(BuildOperationState operationState) {
                setCurrentBuildOperation(parentOperation);
                operationState.setRunning(false);
                LOGGER.debug("Build operation '{}' completed", descriptor.getDisplayName());
            }
        });
    }

    protected <O> O execute(BuildOperationDescriptor descriptor, BuildOperationState operationState, BuildOperationExecution<O> execution, BuildOperationExecutionListener listener) {
        return execution.execute(
            descriptor,
            operationState,
            new DefaultBuildOperationContext(),
            listener
        );
    }

    private void assertParentRunning(String message, BuildOperationDescriptor child, BuildOperationState parent) {
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

    protected void setCurrentBuildOperation(BuildOperationState parentState) {
        currentBuildOperationRef.set(parentState);
    }

    private static BuildOperationState determineParent(BuildOperationDescriptor.Builder descriptorBuilder, @Nullable BuildOperationState defaultParent) {
        BuildOperationState parent = (BuildOperationState) descriptorBuilder.getParentState();
        if (parent == null) {
            parent = defaultParent;
        }
        return parent;
    }

    protected BuildOperationDescriptor createDescriptor(BuildOperationDescriptor.Builder descriptorBuilder, BuildOperationState parent) {
        OperationIdentifier id = new OperationIdentifier(buildOperationIdFactory.nextId());
        return descriptorBuilder.build(id, parent == null
            ? null
            : parent.getDescription().getId());
    }

    protected BuildOperationState getCurrentBuildOperation() {
        return (BuildOperationState) currentBuildOperationRef.get();
    }

    protected interface BuildOperationExecution<O> {
        O execute(BuildOperationDescriptor descriptor, BuildOperationState operationState, DefaultBuildOperationContext context, BuildOperationExecutionListener listener);
    }

    protected interface BuildOperationExecutionListener {
        void start(BuildOperationState operationState);

        void stop(BuildOperationState operationState, DefaultBuildOperationContext context);

        void close(BuildOperationState operationState);
    }

    protected static class DefaultBuildOperationContext implements BuildOperationContext {
        Throwable failure;
        Object result;
        protected String status;

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

    protected static class BuildOperationState implements BuildOperationRef {
        private final BuildOperationDescriptor description;
        private final AtomicBoolean running = new AtomicBoolean();
        private final long startTime;

        public BuildOperationState(BuildOperationDescriptor descriptor, long startTime) {
            this.startTime = startTime;
            this.description = descriptor;
        }

        public BuildOperationDescriptor getDescription() {
            return description;
        }

        public boolean isRunning() {
            return running.get();
        }

        public void setRunning(boolean running) {
            this.running.set(running);
        }

        public long getStartTime() {
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
}
