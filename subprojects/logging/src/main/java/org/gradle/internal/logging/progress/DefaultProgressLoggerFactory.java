/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.progress;

import org.gradle.api.Nullable;
import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.progress.OperationIdentifier;
import org.gradle.internal.time.TimeProvider;
import org.gradle.util.GUtil;

import java.util.concurrent.atomic.AtomicLong;

public class DefaultProgressLoggerFactory implements ProgressLoggerFactory {
    private final ProgressListener progressListener;
    private final TimeProvider timeProvider;
    private final AtomicLong nextId = new AtomicLong(-1);
    private final ThreadLocal<ProgressLoggerImpl> current = new ThreadLocal<ProgressLoggerImpl>();

    public DefaultProgressLoggerFactory(ProgressListener progressListener, TimeProvider timeProvider) {
        this.progressListener = progressListener;
        this.timeProvider = timeProvider;
    }

    public ProgressLogger newOperation(Class loggerCategory) {
        return newOperation(loggerCategory.getName());
    }

    public ProgressLogger newOperation(Class loggerCategory, OperationIdentifier operationIdentifier) {
        return newOperation(loggerCategory.getName());
    }

    public ProgressLogger newOperation(String loggerCategory) {
        return init(loggerCategory, null, null);
    }

    public ProgressLogger newOperation(String loggerCategory, OperationIdentifier operationIdentifier) {
        return init(loggerCategory, null, operationIdentifier);
    }

    public ProgressLogger newOperation(Class loggerClass, ProgressLogger parent) {
        return init(loggerClass.toString(), parent, null);
    }

    private ProgressLogger init(String loggerCategory, @Nullable ProgressLogger parentOperation, @Nullable OperationIdentifier buildOperationIdentifier) {
        if (parentOperation != null && !(parentOperation instanceof ProgressLoggerImpl)) {
            throw new IllegalArgumentException("Unexpected parent logger.");
        }

        // Decrement from -1 to avoid any conflict between this ID and build operation IDs
        OperationIdentifier progressOperationId = new OperationIdentifier(nextId.getAndDecrement());

        return new ProgressLoggerImpl((ProgressLoggerImpl) parentOperation, progressOperationId, loggerCategory, progressListener, timeProvider, buildOperationIdentifier);
    }

    private enum State { idle, started, completed }

    private class ProgressLoggerImpl implements ProgressLogger {
        private final OperationIdentifier operationIdentifier;
        private final OperationIdentifier buildOperationId;
        private final String category;
        private final ProgressListener listener;
        private final TimeProvider timeProvider;
        private ProgressLoggerImpl parent;
        private String description;
        private String shortDescription;
        private String loggingHeader;
        private State state = State.idle;

        public ProgressLoggerImpl(ProgressLoggerImpl parent, OperationIdentifier operationIdentifier, String category, ProgressListener listener, TimeProvider timeProvider, @Nullable OperationIdentifier buildOperationId) {
            this.parent = parent;
            this.operationIdentifier = operationIdentifier;
            this.category = category;
            this.listener = listener;
            this.timeProvider = timeProvider;
            this.buildOperationId = buildOperationId;
        }

        @Override
        public String toString() {
            return category + " - " + description;
        }

        public String getDescription() {
            return description;
        }

        public ProgressLogger setDescription(String description) {
            assertCanConfigure();
            this.description = description;
            return this;
        }

        public String getShortDescription() {
            return shortDescription;
        }

        public ProgressLogger setShortDescription(String shortDescription) {
            assertCanConfigure();
            this.shortDescription = shortDescription;
            return this;
        }

        public String getLoggingHeader() {
            return loggingHeader;
        }

        public ProgressLogger setLoggingHeader(String loggingHeader) {
            assertCanConfigure();
            this.loggingHeader = loggingHeader;
            return this;
        }

        public ProgressLogger start(String description, String shortDescription) {
            setDescription(description);
            setShortDescription(shortDescription);
            started();
            return this;
        }

        public void started() {
            started(null);
        }

        public void started(String status) {
            if (!GUtil.isTrue(description)) {
                throw new IllegalStateException("A description must be specified before this operation is started.");
            }
            assertNotStarted();
            state = State.started;
            if (parent == null) {
                parent = current.get();
            } else {
                parent.assertRunning();
            }
            current.set(this);
            listener.started(new ProgressStartEvent(operationIdentifier, parent == null ? null : parent.operationIdentifier, timeProvider.getCurrentTime(), category, description, shortDescription, loggingHeader, toStatus(status), buildOperationId));
        }

        public void progress(String status) {
            assertRunning();
            listener.progress(new ProgressEvent(operationIdentifier, timeProvider.getCurrentTime(), category, toStatus(status)));
        }

        public void completed() {
            completed(null);
        }

        public void completed(String status) {
            assertRunning();
            state = State.completed;
            current.set(parent);
            listener.completed(new ProgressCompleteEvent(operationIdentifier, timeProvider.getCurrentTime(), category, description, toStatus(status)));
        }

        private String toStatus(String status) {
            return status == null ? "" : status;
        }

        private void assertNotStarted() {
            if (state == State.started) {
                throw new IllegalStateException(String.format("This operation (%s) has already been started.", this));
            }
            if (state == State.completed) {
                throw new IllegalStateException(String.format("This operation (%s) has already completed.", this));
            }
        }

        private void assertRunning() {
            if (state == State.idle) {
                throw new IllegalStateException(String.format("This operation (%s) has not been started.", this));
            }
            if (state == State.completed) {
                throw new IllegalStateException(String.format("This operation (%s) has already been completed.", this));
            }
        }

        private void assertCanConfigure() {
            if (state != State.idle) {
                throw new IllegalStateException(String.format("Cannot configure this operation (%s) once it has started.", this));
            }
        }
    }
}
