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

public class ProgressCompleteEvent extends CategorisedOutputEvent {
    private final String status;
    private final String description;
    private long operationId;

    public ProgressCompleteEvent(long operationId, long timestamp, String category, String description, String status) {
        super(timestamp, category, LogLevel.LIFECYCLE);
        this.operationId = operationId;
        this.status = status;
        this.description = description;
    }

    public String getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("ProgressComplete %s", status);
    }

    public long getOperationId() {
        return operationId;
    }
}
