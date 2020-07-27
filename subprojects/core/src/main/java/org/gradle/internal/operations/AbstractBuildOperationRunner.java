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

import java.io.ObjectStreamException;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractBuildOperationRunner implements BuildOperationRunner {
    protected <O extends BuildOperation> O execute(BuildOperationDescriptor descriptor, BuildOperationState operationState, BuildOperationExecution<O> execution, BuildOperationExecutionListener listener) {
        return execution.execute(
            descriptor,
            operationState,
            new DefaultBuildOperationContext(),
            listener
        );
    }

    protected interface BuildOperationExecution<O extends BuildOperation> {
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
}
