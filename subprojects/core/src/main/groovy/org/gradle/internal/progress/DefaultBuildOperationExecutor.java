/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.progress;

import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.UncheckedException;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultBuildOperationExecutor implements BuildOperationExecutor {
    private final InternalBuildListener listener;
    private final TimeProvider timeProvider;
    private final AtomicLong nextId = new AtomicLong();
    private final ThreadLocal<OperationDetails> currentOperation = new ThreadLocal<OperationDetails>();

    public DefaultBuildOperationExecutor(InternalBuildListener listener, TimeProvider timeProvider) {
        this.listener = listener;
        this.timeProvider = timeProvider;
    }

    @Override
    public Object getCurrentOperationId() {
        OperationDetails current = currentOperation.get();
        if (current == null) {
            throw new IllegalStateException("No operation is currently running.");
        }
        return current.id;
    }

    @Override
    public void run(String displayName, Runnable action) {
        run(displayName, Factories.toFactory(action));
    }

    @Override
    public <T> T run(String displayName, Factory<T> factory) {
        OperationDetails parent = currentOperation.get();
        BuildOperationId parentId = parent == null ? null : parent.id;
        BuildOperationId id = new BuildOperationId(nextId.getAndIncrement());
        currentOperation.set(new OperationDetails(parent, id));
        try {
            long startTime = timeProvider.getCurrentTime();
            BuildOperationInternal operation = new BuildOperationInternal(id, parentId, displayName);
            listener.started(operation, new OperationStartEvent(startTime));

            T result = null;
            Throwable failure = null;
            try {
                result = factory.create();
            } catch (Throwable t) {
                failure = t;
            }

            long endTime = timeProvider.getCurrentTime();
            listener.finished(operation, new OperationResult(startTime, endTime, failure));

            if (failure != null) {
                throw UncheckedException.throwAsUncheckedException(failure);
            }

            return result;
        } finally {
            currentOperation.set(parent);
        }
    }

    private static class OperationDetails {
        final OperationDetails parent;
        final BuildOperationId id;

        public OperationDetails(OperationDetails parent, BuildOperationId id) {
            this.parent = parent;
            this.id = id;
        }
    }

    private static class BuildOperationId implements Serializable {
        private final long id;

        public BuildOperationId(long id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return String.valueOf(id);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            BuildOperationId other = (BuildOperationId) o;
            return id == other.id;
        }

        @Override
        public int hashCode() {
            return (int) id;
        }
    }
}
