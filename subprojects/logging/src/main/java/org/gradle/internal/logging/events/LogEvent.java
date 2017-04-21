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
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.progress.OperationIdentifier;

public class LogEvent extends RenderableOutputEvent {
    private final String message;
    private final Throwable throwable;

    /**
     * Constructs a LogEvent.
     *
     * @deprecated use LogEvent.Builder instead. This constructor will likely be removed in Gradle 5.0
     */
    @Deprecated
    public LogEvent(long timestamp, String category, LogLevel logLevel, String message, @Nullable Throwable throwable) {
        this(timestamp, category, logLevel, message, throwable, null);
    }

    private LogEvent(long timestamp, String category, LogLevel logLevel, String message, @Nullable Throwable throwable, @Nullable OperationIdentifier operationIdentifier) {
        super(timestamp, category, logLevel, operationIdentifier);
        this.message = message;
        this.throwable = throwable;
    }

    public String getMessage() {
        return message;
    }

    @Nullable
    public Throwable getThrowable() {
        return throwable;
    }

    public void render(StyledTextOutput output) {
        output.text(message);
        output.println();
        if (throwable != null) {
            output.exception(throwable);
        }
    }

    @Override
    public String toString() {
        return "[" + getLogLevel() + "] [" + getCategory() + "] " + message;
    }

    public static class Builder {
        private final long timestamp;
        private final String category;
        private final LogLevel logLevel;
        private final String message;
        private @Nullable Throwable throwable;
        private @Nullable OperationIdentifier operationIdentifier;

        public Builder(long timestamp, String category, LogLevel logLevel, String message) {
            this.timestamp = timestamp;
            this.category = category;
            this.logLevel = logLevel;
            this.message = message;
        }

        public LogEvent.Builder withThrowable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public LogEvent.Builder forOperation(OperationIdentifier operationIdentifier) {
            this.operationIdentifier = operationIdentifier;
            return this;
        }

        public LogEvent build() {
            return new LogEvent(timestamp, category, logLevel, message, throwable, operationIdentifier);
        }
    }
}
