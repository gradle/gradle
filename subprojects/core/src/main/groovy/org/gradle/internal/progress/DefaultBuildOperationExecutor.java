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
import org.gradle.internal.logging.events.OperationIdentifier;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

public class DefaultBuildOperationExecutor implements BuildOperationExecutor {
    private final InternalBuildListener listener;
    private final TimeProvider timeProvider;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final AtomicLong nextId = new AtomicLong();
    private final ThreadLocal<OperationDetails> currentOperation = new ThreadLocal<OperationDetails>();

    public DefaultBuildOperationExecutor(InternalBuildListener listener, TimeProvider timeProvider, ProgressLoggerFactory progressLoggerFactory) {
        this.listener = listener;
        this.timeProvider = timeProvider;
        this.progressLoggerFactory = progressLoggerFactory;
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
        run(BuildOperationDetails.displayName(displayName).build(), Factories.toFactory(action));
    }

    @Override
    public void run(BuildOperationDetails operationDetails, Runnable action) {
        run(operationDetails, Factories.toFactory(action));
    }

    @Override
    public <T> T run(String displayName, Factory<T> factory) {
        return run(BuildOperationDetails.displayName(displayName).build(), factory);
    }

    @Override
    public <T> T run(BuildOperationDetails operationDetails, Factory<T> factory) {
        OperationDetails parent = currentOperation.get();
        OperationIdentifier parentId = parent == null ? null : parent.id;
        OperationIdentifier id = new OperationIdentifier(nextId.getAndIncrement());
        currentOperation.set(new OperationDetails(parent, id));
        try {
            long startTime = timeProvider.getCurrentTime();
            BuildOperationInternal operation = new BuildOperationInternal(id, parentId, operationDetails.getDisplayName());
            listener.started(operation, new OperationStartEvent(startTime));

            T result = null;
            Throwable failure = null;
            try {
                ProgressLogger progressLogger;
                if (operationDetails.getProgressDisplayName() != null) {
                    progressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationExecutor.class);
                    progressLogger.setDescription(operationDetails.getDisplayName());
                    progressLogger.setShortDescription(operationDetails.getProgressDisplayName());
                    progressLogger.started();
                } else {
                    progressLogger = null;
                }

                try {
                    result = factory.create();
                } finally {
                    if (progressLogger != null) {
                        progressLogger.completed();
                    }
                }
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
        final OperationIdentifier id;

        public OperationDetails(OperationDetails parent, OperationIdentifier id) {
            this.parent = parent;
            this.id = id;
        }
    }
}
