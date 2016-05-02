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

import org.gradle.api.Nullable;
import org.gradle.api.logging.LogLevel;

public class ProgressStartEvent extends CategorisedOutputEvent {
    private final OperationIdentifier operationId;
    private final OperationIdentifier parentId;
    private final String description;
    private final String shortDescription;
    private final String loggingHeader;
    private final String status;

    public ProgressStartEvent(OperationIdentifier operationId, @Nullable OperationIdentifier parentId, long timestamp, String category, String description, @Nullable String shortDescription, @Nullable String loggingHeader, String status) {
        super(timestamp, category, LogLevel.LIFECYCLE);
        this.operationId = operationId;
        this.parentId = parentId;
        this.description = description;
        this.shortDescription = shortDescription;
        this.loggingHeader = loggingHeader;
        this.status = status;
    }

    @Nullable
    public OperationIdentifier getParentId() {
        return parentId;
    }

    public String getDescription() {
        return description;
    }

    @Nullable
    public String getShortDescription() {
        return shortDescription;
    }

    @Nullable
    public String getLoggingHeader() {
        return loggingHeader;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "ProgressStart " + description;
    }

    public OperationIdentifier getOperationId() {
        return operationId;
    }
}
