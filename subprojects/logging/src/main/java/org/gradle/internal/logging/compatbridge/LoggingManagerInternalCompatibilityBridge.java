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
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.internal.logging.LoggingManagerInternal;

import java.io.OutputStream;

/**
 * 2.14 moved LoggingManagerInternal. This type is exposed via AbstractTask.getLogging(). This “bridge” exists purely to maintain compatibility there.
 */
@SuppressWarnings("deprecation")
public class LoggingManagerInternalCompatibilityBridge implements org.gradle.logging.LoggingManagerInternal {

    private final LoggingManagerInternal delegate;

    public LoggingManagerInternalCompatibilityBridge(LoggingManagerInternal delegate) {
        this.delegate = delegate;
    }

    @Override
    public org.gradle.logging.LoggingManagerInternal start() {
        delegate.start();
        return this;
    }

    @Override
    public org.gradle.logging.LoggingManagerInternal stop() {
        delegate.stop();
        return this;
    }

    @Override
    public org.gradle.logging.LoggingManagerInternal captureSystemSources() {
        delegate.captureSystemSources();
        return this;
    }

    @Override
    public org.gradle.logging.LoggingManagerInternal captureStandardOutput(LogLevel level) {
        delegate.captureStandardOutput(level);
        return this;
    }

    @Override
    public org.gradle.logging.LoggingManagerInternal captureStandardError(LogLevel level) {
        delegate.captureStandardError(level);
        return this;
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
    public org.gradle.logging.LoggingManagerInternal setLevel(LogLevel logLevel) {
        delegate.setLevelInternal(logLevel);
        return this;
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
}
