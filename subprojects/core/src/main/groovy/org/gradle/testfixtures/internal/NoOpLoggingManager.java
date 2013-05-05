/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.testfixtures.internal;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.internal.OutputEventListener;

public class NoOpLoggingManager implements LoggingManagerInternal {
    private LogLevel level = LogLevel.LIFECYCLE;
    private LogLevel stdoutLevel = LogLevel.LIFECYCLE;

    public LoggingManagerInternal captureStandardOutput(LogLevel level) {
        stdoutLevel = level;
        return this;
    }

    public LogLevel getStandardOutputCaptureLevel() {
        return stdoutLevel;
    }

    public LoggingManagerInternal captureStandardError(LogLevel level) {
        return this;
    }

    public LogLevel getLevel() {
        return level;
    }

    public LoggingManagerInternal setLevel(LogLevel level) {
        this.level = level;
        return this;
    }

    public LogLevel getStandardErrorCaptureLevel() {
        return LogLevel.ERROR;
    }

    public LoggingManagerInternal start() {
        return this;
    }

    public LoggingManagerInternal stop() {
        return this;
    }

    public void addStandardErrorListener(StandardOutputListener listener) {
    }

    public void addStandardOutputListener(StandardOutputListener listener) {
    }

    public void removeStandardOutputListener(StandardOutputListener listener) {
    }

    public void removeStandardErrorListener(StandardOutputListener listener) {
    }

    public void addOutputEventListener(OutputEventListener listener) {
    }

    public void removeOutputEventListener(OutputEventListener listener) {
    }

    public void attachConsole(boolean colorOutput) {
    }

    public void addStandardOutputAndError() {
    }
}
