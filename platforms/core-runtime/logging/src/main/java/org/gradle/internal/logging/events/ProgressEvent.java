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
import org.gradle.internal.operations.OperationIdentifier;

public class ProgressEvent extends OutputEvent {
    private final String status;
    private final boolean failing;
    private final OperationIdentifier progressOperationId;

    public ProgressEvent(OperationIdentifier progressOperationId, String status, boolean failing) {
        this.progressOperationId = progressOperationId;
        this.status = status;
        this.failing = failing;
    }

    public String getStatus() {
        return status;
    }

    public boolean isFailing() {
        return failing;
    }

    @Override
    public String toString() {
        return "Progress (" + progressOperationId + ") " + status;
    }

    public OperationIdentifier getProgressOperationId() {
        return progressOperationId;
    }

    @Override
    public LogLevel getLogLevel() {
        return LogLevel.LIFECYCLE;
    }
}
