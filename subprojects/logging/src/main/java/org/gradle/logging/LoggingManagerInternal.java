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

package org.gradle.logging;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.LoggingManager;
import org.gradle.api.logging.StandardOutputListener;

import java.io.OutputStream;

/**
 * This type was accidentally leaked into the public API, please do not refer to it.
 * Use {@link org.gradle.api.logging.LoggingManager} instead.
 */
@Deprecated
public interface LoggingManagerInternal extends LoggingManager, StandardOutputCapture {
    LogLevel getLevel();

    LoggingManagerInternal setLevel(LogLevel logLevel);

    LoggingManagerInternal start();

    LoggingManagerInternal stop();

    LoggingManagerInternal captureSystemSources();

    LoggingManagerInternal captureStandardOutput(LogLevel level);

    LoggingManagerInternal captureStandardError(LogLevel level);

    LogLevel getStandardErrorCaptureLevel();

    LogLevel getStandardOutputCaptureLevel();

    void addStandardOutputListener(OutputStream outputStream);

    void addStandardErrorListener(OutputStream outputStream);

    void addStandardOutputListener(StandardOutputListener listener);

    void addStandardErrorListener(StandardOutputListener listener);

    void removeStandardOutputListener(StandardOutputListener listener);

    void removeStandardErrorListener(StandardOutputListener listener);

    void attachAnsiConsole(OutputStream outputStream);

    void attachSystemOutAndErr();
}
