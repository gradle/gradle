/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.time.Clock;

import javax.annotation.Nullable;

/**
 * Adapts the {@link DefaultBuildOperationRunner.BuildOperationExecutionListener} to the {@link BuildOperationListener} and the {@link ProgressLogger} interfaces.
 *
 * This notification of build operation execution can be received by tooling API clients.
 * The adapter also generates progress logging events.
 *
 * TODO Separate these two purposes into separate classes
 */
public class BuildOperationProgressEventListenerAdapter implements DefaultBuildOperationRunner.BuildOperationExecutionListener {
    private final BuildOperationListener buildOperationListener;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final Clock clock;
    private ProgressLogger progressLogger;
    private ProgressLogger statusProgressLogger;

    public BuildOperationProgressEventListenerAdapter(BuildOperationListener buildOperationListener, ProgressLoggerFactory progressLoggerFactory, Clock clock) {
        this.buildOperationListener = buildOperationListener;
        this.progressLoggerFactory = progressLoggerFactory;
        this.clock = clock;
    }

    @Override
    public void start(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
        buildOperationListener.started(descriptor, new OperationStartEvent(operationState.getStartTime()));
        ProgressLogger progressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationRunner.class, descriptor);
        this.progressLogger = progressLogger.start(descriptor.getDisplayName(), descriptor.getProgressDisplayName());
    }

    @Override
    public void progress(BuildOperationDescriptor descriptor, String status) {
        // Currently, need to start a new progress operation to hold the status, as changing the status of the progress operation replaces the
        // progress display name on the console, whereas we want to display both the progress display name and the status
        // This should be pushed down into the progress logger infrastructure so that an operation can have both a display name (that doesn't change) and
        // a status (that does)
        if (statusProgressLogger == null) {
            statusProgressLogger = progressLoggerFactory.newOperation(DefaultBuildOperationRunner.class, progressLogger);
            statusProgressLogger.start(descriptor.getDisplayName(), status);
        } else {
            statusProgressLogger.progress(status);
        }
    }

    @Override
    public void progress(BuildOperationDescriptor descriptor, long progress, long total, String units, String status) {
        progress(descriptor, status);
        buildOperationListener.progress(descriptor.getId(), new OperationProgressEvent(clock.getTimestamp(), new OperationProgressDetails(progress, total, units)));
    }

    @Override
    public void stop(BuildOperationDescriptor descriptor, BuildOperationState operationState, @Nullable BuildOperationState parent, DefaultBuildOperationRunner.ReadableBuildOperationContext context) {
        if (statusProgressLogger != null) {
            statusProgressLogger.completed();
        }
        progressLogger.completed(context.getStatus(), context.getFailure() != null);
        buildOperationListener.finished(descriptor, new OperationFinishEvent(operationState.getStartTime(), clock.getTimestamp(), context.getFailure(), context.getResult()));
    }

    @Override
    public void close(BuildOperationDescriptor descriptor, BuildOperationState operationState) {
    }
}
