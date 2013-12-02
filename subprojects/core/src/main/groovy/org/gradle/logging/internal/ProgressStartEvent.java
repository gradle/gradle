/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.logging.internal;

import org.gradle.api.logging.LogLevel;

public class ProgressStartEvent extends CategorisedOutputEvent {
    private long operationId;
    private Long parentOperationId;
    private final String description;
    private final String shortDescription;
    private final String loggingHeader;
    private final String status;

    public ProgressStartEvent(long operationId, Long parentOperationId, long timestamp, String category, String description, String shortDescription, String loggingHeader, String status) {
        super(timestamp, category, LogLevel.LIFECYCLE);
        this.operationId = operationId;
        this.parentOperationId = parentOperationId;
        this.description = description;
        this.shortDescription = shortDescription;
        this.loggingHeader = loggingHeader;
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getLoggingHeader() {
        return loggingHeader;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return String.format("ProgressStart %s", description);
    }

    public long getOperationId() {
        return operationId;
    }

    public Long getParentOperationId() {
        return parentOperationId;
    }
}
