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

/**
 * <p>An extension to the SLF4J {@code Logger} interface, which adds the {@code quiet} and {@code lifecycle} log
 * levels.</p>
 *
 * <p>You can obtain a {@code Logger} instance using {@link Logging#getLogger(Class)} or {@link
 * Logging#getLogger(String)}. A {@code Logger} instance is also available through {@link
 * org.gradle.api.Project#getLogger()}, {@link org.gradle.api.Task#getLogger()} and {@link
 * org.gradle.api.Script#getLogger()}.</p>
 */
public interface Logger extends org.slf4j.Logger {
    /**
     * Returns true if lifecycle log level is enabled for this logger.
     */
    boolean isLifecycleEnabled();

    /**
     * Multiple-parameters friendly debug method
     *
     * @param message the log message
     * @param objects the log message parameters
     */
    @Override
    void debug(String message, Object... objects);

    /**
     * Logs the given message at lifecycle log level.
     *
     * @param message the log message.
     */
    void lifecycle(String message);

    /**
     * Logs the given message at lifecycle log level.
     *
     * @param message the log message.
     * @param objects the log message parameters.
     */
    void lifecycle(String message, Object... objects);

    /**
     * Logs the given message at lifecycle log level.
     *
     * @param message the log message.
     * @param throwable the exception to log.
     */
    void lifecycle(String message, Throwable throwable);

    /**
     * Returns true if quiet log level is enabled for this logger.
     */
    boolean isQuietEnabled();

    /**
     * Logs the given message at quiet log level.
     *
     * @param message the log message.
     */
    void quiet(String message);

    /**
     * Logs the given message at quiet log level.
     *
     * @param message the log message.
     * @param objects the log message parameters.
     */
    void quiet(String message, Object... objects);

    /**
     * Logs the given message at info log level.
     *
     * @param message the log message.
     * @param objects the log message parameters.
     */
    @Override
    void info(String message, Object... objects);

    /**
     * Logs the given message at quiet log level.
     *
     * @param message the log message.
     * @param throwable the exception to log.
     */
    void quiet(String message, Throwable throwable);

    /**
     * Returns true if the given log level is enabled for this logger.
     */
    boolean isEnabled(LogLevel level);

    /**
     * Logs the given message at the given log level.
     *
     * @param level the log level.
     * @param message the log message.
     */
    void log(LogLevel level, String message);

    /**
     * Logs the given message at the given log level.
     *
     * @param level the log level.
     * @param message the log message.
     * @param objects the log message parameters.
     */
    void log(LogLevel level, String message, Object... objects);

    /**
     * Logs the given message at the given log level.
     *
     * @param level the log level.
     * @param message the log message.
     * @param throwable the exception to log.
     */
    void log(LogLevel level, String message, Throwable throwable);
}
