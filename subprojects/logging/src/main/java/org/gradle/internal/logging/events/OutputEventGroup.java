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

import org.gradle.internal.progress.BuildOperationCategory;

import java.util.List;

public class OutputEventGroup {
    private final long timestamp;
    private final String category;
    private final String loggingHeader;
    private final String description;
    private final String shortDescription;
    private final String status;
    private final List<RenderableOutputEvent> logs;
    private final Object buildOperationId;
    private final BuildOperationCategory buildOperationCategory;

    public OutputEventGroup(long timestamp, String category, String loggingHeader, String description, String shortDescription, String status, List<RenderableOutputEvent> logs, Object buildOperationId, BuildOperationCategory buildOperationCategory) {
        this.timestamp = timestamp;
        this.category = category;
        this.loggingHeader = loggingHeader;
        this.description = description;
        this.shortDescription = shortDescription;
        this.status = status;
        this.logs = logs;
        this.buildOperationId = buildOperationId;
        this.buildOperationCategory = buildOperationCategory;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getCategory() {
        return category;
    }

    public String getLoggingHeader() {
        return loggingHeader;
    }

    public String getDescription() {
        return description;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getStatus() {
        return status;
    }

    public List<RenderableOutputEvent> getLogs() {
        return logs;
    }

    public Object getBuildOperationId() {
        return buildOperationId;
    }

    public BuildOperationCategory getBuildOperationCategory() {
        return buildOperationCategory;
    }

    @Override
    public String toString() {
        return "OutputEventGroup{"
            + "description='" + description + '\''
            + ", numLogs='" + getLogs().size() + '\''
            + ", status='" + status + '\''
            + ", buildOpIdentifier=" + buildOperationId
            + '}';
    }
}
