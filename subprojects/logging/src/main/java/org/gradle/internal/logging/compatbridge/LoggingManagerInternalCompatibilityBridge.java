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

package org.gradle.internal.logging.compatbridge;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.OutputEventListener;

import java.io.OutputStream;

/**
 * 2.14 moved LoggingManagerInternal.
 * This type is exposed via AbstractTask.getLogging().
 * This “bridge” exists purely to maintain compatibility there.
 */
public class LoggingManagerInternalCompatibilityBridge implements org.gradle.logging.LoggingManagerInternal {

    private final LoggingManagerInternal delegate;

    public LoggingManagerInternalCompatibilityBridge(LoggingManagerInternal delegate) {
        this.delegate = delegate;
    }

    @Override
    public LoggingManagerInternal start() {
        return delegate.start();
    }

    @Override
    public LoggingManagerInternal stop() {
        return delegate.stop();
    }

    @Override
    public LoggingManagerInternal captureSystemSources() {
        return delegate.captureSystemSources();
    }

    @Override
    public LoggingManagerInternal captureStandardOutput(LogLevel level) {
        return delegate.captureStandardOutput(level);
    }

    @Override
    public LoggingManagerInternal captureStandardError(LogLevel level) {
        return delegate.captureStandardError(level);
    }

    @Override
    public LoggingManagerInternal setLevelInternal(LogLevel logLevel) {
        return delegate.setLevelInternal(logLevel);
    }

    @Override
    public LogLevel getStandardOutputCaptureLevel() {
        return delegate.getStandardOutputCaptureLevel();
    }

    @Override
    public LogLevel getStandardErrorCaptureLevel() {
        return delegate.getStandardErrorCaptureLevel();
    }

    @Override
    public LogLevel getLevel() {
        return delegate.getLevel();
    }

    @Override
    @Deprecated
    public LoggingManager setLevel(LogLevel logLevel) {
        return delegate.setLevel(logLevel);
    }

    @Override
    public void addStandardOutputListener(StandardOutputListener listener) {
        delegate.addStandardOutputListener(listener);
    }

    @Override
    public void removeStandardOutputListener(StandardOutputListener listener) {
        delegate.removeStandardOutputListener(listener);
    }

    @Override
    public void addStandardErrorListener(StandardOutputListener listener) {
        delegate.addStandardErrorListener(listener);
    }

    @Override
    public void removeStandardErrorListener(StandardOutputListener listener) {
        delegate.removeStandardErrorListener(listener);
    }

    @Override
    public void attachSystemOutAndErr() {
        delegate.attachSystemOutAndErr();
    }

    @Override
    public void attachProcessConsole(ConsoleOutput consoleOutput) {
        delegate.attachProcessConsole(consoleOutput);
    }

    @Override
    public void attachAnsiConsole(OutputStream outputStream) {
        delegate.attachAnsiConsole(outputStream);
    }

    @Override
    public void addStandardOutputListener(OutputStream outputStream) {
        delegate.addStandardOutputListener(outputStream);
    }

    @Override
    public void addStandardErrorListener(OutputStream outputStream) {
        delegate.addStandardErrorListener(outputStream);
    }

    @Override
    public void addOutputEventListener(OutputEventListener listener) {
        delegate.addOutputEventListener(listener);
    }

    @Override
    public void removeOutputEventListener(OutputEventListener listener) {
        delegate.removeOutputEventListener(listener);
    }
}
