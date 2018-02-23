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

public class ProgressCompleteEvent extends OutputEvent {
    private final long timestamp;
    private final String status;
    private OperationIdentifier progressOperationId;
    private boolean failed;

    public ProgressCompleteEvent(OperationIdentifier progressOperationId, long timestamp, String status, boolean failed) {
        this.progressOperationId = progressOperationId;
        this.timestamp = timestamp;
        this.status = status;
        this.failed = failed;
    }

    public String getStatus() {
        return status;
    }

    public boolean isFailed() {
        return failed;
    }

    @Override
    public String toString() {
        return "ProgressComplete " + status;
    }

    public OperationIdentifier getProgressOperationId() {
        return progressOperationId;
    }

    @Override
    public LogLevel getLogLevel() {
        return LogLevel.LIFECYCLE;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
