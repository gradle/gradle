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

import java.util.LinkedList;

// TODO - thread safety
public class DefaultBuildOperationExecutor implements BuildOperationExecutor {
    private final InternalBuildListener listener;
    private final TimeProvider timeProvider;
    private LinkedList<Object> operationStack = new LinkedList<Object>();

    public DefaultBuildOperationExecutor(InternalBuildListener listener, TimeProvider timeProvider) {
        this.listener = listener;
        this.timeProvider = timeProvider;
    }

    @Override
    public Object getCurrentOperationId() {
        if (operationStack.isEmpty()) {
            throw new IllegalStateException("No operation is currently running.");
        }
        return operationStack.getFirst();
    }

    @Override
    public void run(Object id, BuildOperationType operationType, Runnable action) {
        run(id, operationType, Factories.toFactory(action));
    }

    @Override
    public <T> T run(Object id, BuildOperationType operationType, Factory<T> factory) {
        Object parentId = operationStack.isEmpty() ? null : operationStack.getFirst();
        operationStack.addFirst(id);
        try {
            long startTime = timeProvider.getCurrentTime();
            BuildOperationInternal operation = new BuildOperationInternal(id, parentId, operationType);
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
            operationStack.removeFirst();
        }
    }
}
