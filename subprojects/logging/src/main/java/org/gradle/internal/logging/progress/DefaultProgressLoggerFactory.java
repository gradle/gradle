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

import org.gradle.internal.logging.events.ProgressCompleteEvent;
import org.gradle.internal.logging.events.ProgressEvent;
import org.gradle.internal.logging.events.ProgressStartEvent;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.OperationIdentifier;
import org.gradle.internal.time.Clock;
import org.gradle.util.GUtil;

import javax.annotation.Nullable;

public class DefaultProgressLoggerFactory implements ProgressLoggerFactory {
    private final ProgressListener progressListener;
    private final Clock clock;
    private final BuildOperationIdFactory buildOperationIdFactory;
    private final ThreadLocal<ProgressLoggerImpl> current = new ThreadLocal<ProgressLoggerImpl>();
    private final CurrentBuildOperationRef currentBuildOperationRef = CurrentBuildOperationRef.instance();

    public DefaultProgressLoggerFactory(ProgressListener progressListener, Clock clock, BuildOperationIdFactory buildOperationIdFactory) {
        this.progressListener = progressListener;
        this.clock = clock;
        this.buildOperationIdFactory = buildOperationIdFactory;
    }

    public ProgressLogger newOperation(Class loggerCategory) {
        return newOperation(loggerCategory.getName());
    }

    public ProgressLogger newOperation(Class loggerCategory, @Nullable BuildOperationDescriptor buildOperationDescriptor) {
        ProgressLoggerImpl logger = new ProgressLoggerImpl(
            null,
            buildOperationDescriptor.getId(),
            loggerCategory.getName(),
            progressListener,
            clock,
            true,
            buildOperationDescriptor.getId(),
            buildOperationDescriptor.getParentId(),
            buildOperationDescriptor.getOperationType()
        );
        logger.totalProgress = buildOperationDescriptor.getTotalProgress();

        // Make some assumptions about the console output
        if (buildOperationDescriptor.getOperationType() == BuildOperationCategory.TASK) {
            logger.setLoggingHeader(buildOperationDescriptor.getProgressDisplayName());
        }

        return logger;
    }

    public ProgressLogger newOperation(String loggerCategory) {
        return init(loggerCategory, null);
    }

    public ProgressLogger newOperation(Class loggerClass, ProgressLogger parent) {
        return init(loggerClass.toString(), parent);
    }

    private ProgressLogger init(
        String loggerCategory,
        @Nullable ProgressLogger parentOperation
    ) {
        if (parentOperation != null && !(parentOperation instanceof ProgressLoggerImpl)) {
            throw new IllegalArgumentException("Unexpected parent logger.");
        }
        return new ProgressLoggerImpl(
            (ProgressLoggerImpl) parentOperation,
            new OperationIdentifier(buildOperationIdFactory.nextId()),
            loggerCategory,
            progressListener,
            clock,
            false,
            currentBuildOperationRef.getId(),
            currentBuildOperationRef.getParentId(),
            null
        );
    }

    private enum State {idle, started, completed}

    private class ProgressLoggerImpl implements ProgressLogger {
        private final OperationIdentifier progressOperationId;
        private final String category;
        private final ProgressListener listener;
        private final Clock clock;
        private final boolean buildOperationStart;
        @Nullable
        private final OperationIdentifier buildOperationId;
        @Nullable
        private final OperationIdentifier parentBuildOperationId;
        private final BuildOperationCategory buildOperationCategory;
        private ProgressLoggerImpl previous;
        private ProgressLoggerImpl parent;
        private String description;
        private String shortDescription;
        private String loggingHeader;
        private State state = State.idle;
        private int totalProgress;

        ProgressLoggerImpl(
            ProgressLoggerImpl parent,
            OperationIdentifier progressOperationId,
            String category,
            ProgressListener listener,
            Clock clock,
            boolean buildOperationStart,
            @Nullable OperationIdentifier buildOperationId,
            @Nullable OperationIdentifier parentBuildOperationId,
            @Nullable BuildOperationCategory buildOperationCategory
        ) {
            this.parent = parent;
            this.progressOperationId = progressOperationId;
            this.category = category;
            this.listener = listener;
            this.clock = clock;
            this.buildOperationStart = buildOperationStart;
            this.buildOperationId = buildOperationId;
            this.parentBuildOperationId = parentBuildOperationId;
            this.buildOperationCategory = buildOperationCategory;
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
            start(description, shortDescription, totalProgress);
            return this;
        }

        public ProgressLogger start(String description, String shortDescription, int totalProgress) {
            setDescription(description);
            setShortDescription(shortDescription);
            started(null, totalProgress);
            return this;
        }

        public void started() {
            started(null);
        }

        public void started(String status) {
            started(status, 0);
        }

        public void started(String status, int totalProgress) {
            if (!GUtil.isTrue(description)) {
                throw new IllegalStateException("A description must be specified before this operation is started.");
            }
            assertNotStarted();
            state = State.started;
            previous = current.get();
            OperationIdentifier parentProgressId;
            if (parent == null) {
                if (previous != null) {
                    parent = previous;
                    parentProgressId = parent.progressOperationId;
                } else if (buildOperationStart) {
                    parentProgressId = parentBuildOperationId;
                } else {
                    parentProgressId = buildOperationId;
                }
            } else {
                parentProgressId = parent.progressOperationId;
                parent.assertRunning();
            }
            current.set(this);
            listener.started(new ProgressStartEvent(
                progressOperationId,
                parentProgressId,
                clock.getCurrentTime(),
                category,
                description,
                shortDescription,
                loggingHeader,
                ensureNotNull(status),
                totalProgress,
                buildOperationStart,
                buildOperationId,
                parentBuildOperationId,
                buildOperationCategory
            ));
        }

        public void progress(String status) {
            progress(status, false);
        }

        public void progress(String status, boolean failing) {
            assertRunning();
            listener.progress(new ProgressEvent(progressOperationId, ensureNotNull(status), failing));
        }

        public void completed() {
            completed(null, false);
        }

        public void completed(String status, boolean failed) {
            assertRunning();
            state = State.completed;
            current.set(previous);
            listener.completed(new ProgressCompleteEvent(progressOperationId, clock.getCurrentTime(), ensureNotNull(status), failed));
        }

        private String ensureNotNull(String status) {
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
