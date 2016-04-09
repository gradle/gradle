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

package org.gradle.api.logging.configuration;

import org.gradle.api.logging.LogLevel;

/**
 * A {@code LoggingConfiguration} defines the logging settings for a Gradle build.
 */
public interface LoggingConfiguration {
    /**
     * Returns the minimum logging level to use. All log messages with a lower log level are ignored.
     * Defaults to {@link LogLevel#LIFECYCLE}.
     */
    LogLevel getLogLevel();

    /**
     * Specifies the minimum logging level to use. All log messages with a lower log level are ignored.
     */
    void setLogLevel(LogLevel logLevel);

    /**
     * Returns true if logging output should be displayed in color when Gradle is running in a console which supports
     * color output. The default value is true.
     *
     * @return true if logging output should be displayed in color.
     * @deprecated Use {@link #getConsoleOutput()} instead.
     */
    @Deprecated
    boolean isColorOutput();

    /**
     * Specifies whether logging output should be displayed in color.
     *
     * @param colorOutput true if logging output should be displayed in color.
     * @deprecated Use {@link #setConsoleOutput(ConsoleOutput)} instead.
     */
    @Deprecated
    void setColorOutput(boolean colorOutput);

    /**
     * Returns the style of logging output that should be written to the console.
     * Defaults to {@link ConsoleOutput#Auto}
     */
    ConsoleOutput getConsoleOutput();

    /**
     * Specifies the style of logging output that should be written to the console.
     */
    void setConsoleOutput(ConsoleOutput colorOutput);

    /**
     * Returns the detail that should be included in stacktraces. Defaults to {@link ShowStacktrace#INTERNAL_EXCEPTIONS}.
     */
    ShowStacktrace getShowStacktrace();

    /**
     * Sets the detail that should be included in stacktraces.
     */
    void setShowStacktrace(ShowStacktrace showStacktrace);
}
