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
import org.gradle.internal.instrumentation.api.annotations.NotToBeMigratedToLazy;

/**
 * <p>A {@code LoggingConfiguration} defines the logging settings for a Gradle instance.</p>
 */
@NotToBeMigratedToLazy
public interface LoggingConfiguration {
    /**
     * Returns the minimum logging level to use. All messages at a lower level are discarded.
     *
     * @return The minimum logging level.
     */
    LogLevel getLogLevel();

    /**
     * Sets the minimum logging level to use.
     *
     * @param logLevel The minimum logging level.
     */
    void setLogLevel(LogLevel logLevel);

    /**
     * Returns the categories of stacktrace to show.
     *
     * @return The stacktrace display options.
     */
    ShowStacktrace getShowStacktrace();

    /**
     * Sets the categories of stacktrace to show.
     *
     * @param showStacktrace The stacktrace display options.
     */
    void setShowStacktrace(ShowStacktrace showStacktrace);

    /**
     * Returns the console output type.
     *
     * @return The console output type.
     */
    ConsoleOutput getConsoleOutput();

    /**
     * Sets the console output type.
     *
     * @param consoleOutput The console output type.
     */
    void setConsoleOutput(ConsoleOutput consoleOutput);

    /**
     * Returns the navigation bar colorization setting.
     *
     * @return The navigation bar colorization setting.
     * @since 8.7
     */
    default NavigationBarColorization getNavigationBarColorization() {
        return NavigationBarColorization.AUTO;
    }

    /**
     * Sets the navigation bar colorization setting.
     *
     * @param colorization The navigation bar colorization setting.
     * @since 8.7
     */
    default void setNavigationBarColorization(NavigationBarColorization colorization) {
        // Default implementation does nothing
    }

    /**
     * Returns the warning mode.
     *
     * @return The warning mode.
     */
    WarningMode getWarningMode();

    /**
     * Sets the warning mode.
     *
     * @param warningMode The warning mode.
     */
    void setWarningMode(WarningMode warningMode);
}
