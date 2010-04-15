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

package org.gradle.api.logging;

import org.gradle.logging.LoggingSystem;

/**
 * @author Hans Dockter
 */
public class DefaultStandardOutputCapture implements LoggingManager {
    private LogLevel stdOutCaptureLevel;
    private LogLevel level;
    private LoggingSystem.Snapshot originalStdOutState;
    private LoggingSystem.Snapshot originalLoggingState;
    private boolean started;
    private final LoggingSystem loggingSystem;
    private final LoggingSystem stdOutLoggingSystem;

    /**
     * Creates and instance with enabled set to false and LogLevel set to null.
     */
    public DefaultStandardOutputCapture(LoggingSystem loggingSystem, LoggingSystem stdOutLoggingSystem) {
        this.loggingSystem = loggingSystem;
        this.stdOutLoggingSystem = stdOutLoggingSystem;
        stdOutCaptureLevel = LogLevel.QUIET;
    }

    public DefaultStandardOutputCapture start() {
        started = true;
        if (stdOutCaptureLevel != null) {
            originalStdOutState = stdOutLoggingSystem.on(stdOutCaptureLevel);
        } else {
            originalStdOutState = stdOutLoggingSystem.off();
        }
        if (level != null) {
            originalLoggingState = loggingSystem.on(level);
        } else {
            originalLoggingState = loggingSystem.snapshot();
        }

        return this;
    }

    public DefaultStandardOutputCapture stop() {
        try {
            if (originalStdOutState != null) {
                stdOutLoggingSystem.restore(originalStdOutState);
            }
            if (originalLoggingState != null) {
                loggingSystem.restore(originalLoggingState);
            }
        } finally {
            originalStdOutState = null;
            originalLoggingState = null;
            started = false;
        }
        return this;
    }

    public void setLevel(LogLevel logLevel) {
        if (this.level != logLevel) {
            this.level = logLevel;
            if (started) {
                loggingSystem.on(logLevel);
            }
        }
    }

    public LogLevel getLevel() {
        return level;
    }

    public LogLevel getStandardOutputCaptureLevel() {
        return stdOutCaptureLevel;
    }

    public boolean isStandardOutputCaptureEnabled() {
        return stdOutCaptureLevel != null;
    }

    public DefaultStandardOutputCapture captureStandardOutput(LogLevel level) {
        if (this.stdOutCaptureLevel != level) {
            this.stdOutCaptureLevel = level;
            if (started) {
                stdOutLoggingSystem.on(level);
            }
        }
        return this;
    }

    public DefaultStandardOutputCapture disableStandardOutputCapture() {
        if (stdOutCaptureLevel != null) {
            stdOutCaptureLevel = null;
            if (started) {
                stdOutLoggingSystem.off();
            }
        }
        return this;
    }
}
