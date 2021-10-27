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

package org.gradle.internal.logging.events;

import org.gradle.api.logging.LogLevel;
import org.gradle.internal.logging.events.operations.ProgressStartBuildOperationProgressDetails;
import org.gradle.internal.operations.BuildOperationCategory;
import org.gradle.internal.operations.OperationIdentifier;

import javax.annotation.Nullable;

public class ProgressStartEvent extends CategorisedOutputEvent implements ProgressStartBuildOperationProgressDetails {
    public static final String TASK_CATEGORY = "class org.gradle.internal.buildevents.TaskExecutionLogger";
    public static final String BUILD_OP_CATEGORY = "org.gradle.internal.logging.progress.ProgressLoggerFactory";

    private final OperationIdentifier progressOperationId;
    private final OperationIdentifier parentProgressOperationId;
    private final String description;
    private final @Nullable String loggingHeader;
    private final String status;
    private final int totalProgress;
    private final boolean buildOperationStart;
    private final @Nullable OperationIdentifier buildOperationId;
    private final BuildOperationCategory buildOperationCategory;

    public ProgressStartEvent(
        OperationIdentifier progressOperationId,
        @Nullable OperationIdentifier parentProgressOperationId,
        long timestamp,
        String category,
        String description,
        @Nullable String loggingHeader,
        String status,
        int totalProgress,
        boolean buildOperationStart,
        @Nullable OperationIdentifier buildOperationId,
        @Nullable BuildOperationCategory buildOperationCategory
    ) {
        super(timestamp, category, LogLevel.LIFECYCLE);
        this.progressOperationId = progressOperationId;
        this.parentProgressOperationId = parentProgressOperationId;
        this.description = description;
        this.loggingHeader = loggingHeader;
        this.status = status;
        this.totalProgress = totalProgress;
        this.buildOperationStart = buildOperationStart;
        this.buildOperationId = buildOperationId;
        this.buildOperationCategory = buildOperationCategory == null ? BuildOperationCategory.UNCATEGORIZED : buildOperationCategory;
    }

    @Nullable
    public OperationIdentifier getParentProgressOperationId() {
        return parentProgressOperationId;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    @Nullable
    public String getLoggingHeader() {
        return loggingHeader;
    }

    public String getStatus() {
        return status;
    }

    public int getTotalProgress() {
        return totalProgress;
    }

    @Override
    public String toString() {
        return "ProgressStart (p:" + progressOperationId + " parent p:" + parentProgressOperationId + " b:" + buildOperationId + ") " + description;
    }

    public OperationIdentifier getProgressOperationId() {
        return progressOperationId;
    }

    /**
     * Whether this progress start represent the start of a build operation,
     * as opposed to a progress operation within a build operation.
     */
    public boolean isBuildOperationStart() {
        return buildOperationStart;
    }

    /**
     * When this event is a build operation start event, this property will be non-null and will have the same value as {@link #getProgressOperationId()}.
     */
    @Nullable
    public OperationIdentifier getBuildOperationId() {
        return buildOperationId;
    }

    public BuildOperationCategory getBuildOperationCategory() {
        return buildOperationCategory;
    }

    public ProgressStartEvent withParentProgressOperation(OperationIdentifier parentProgressOperationId) {
        return new ProgressStartEvent(progressOperationId, parentProgressOperationId, getTimestamp(), getCategory(), description, loggingHeader, status, totalProgress, buildOperationStart, buildOperationId, buildOperationCategory);
    }
}
