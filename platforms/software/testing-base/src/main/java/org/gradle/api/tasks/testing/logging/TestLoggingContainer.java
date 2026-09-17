/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.testing.logging;

import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Internal;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

/**
 * Container for all test logging related options. Different options
 * can be set for each log level. Options that are set directly (without
 * specifying a log level) apply to log level LIFECYCLE. Example:
 *
 * <pre class='autoTested'>
 * apply plugin: 'java'
 *
 * test {
 *     testLogging {
 *         // set options for log level LIFECYCLE
 *         events("failed")
 *         exceptionFormat = "short"
 *
 *         // set options for log level DEBUG
 *         debug {
 *             events("started", "skipped", "failed")
 *             exceptionFormat = "full"
 *         }
 *
 *         // remove standard output/error logging from --info builds
 *         // by assigning only 'failed' and 'skipped' events
 *         info.events = ["failed", "skipped"]
 *     }
 * }
 * </pre>
 *
 * The defaults that are in place show progressively more information
 * on log levels WARN, LIFECYCLE, INFO, and DEBUG, respectively.
 * @since 1.1
 */
public interface TestLoggingContainer extends TestLogging {
    /**
     * Returns logging options for debug level.
     *
     * @return logging options for debug level
     * @since 1.1
     */
    @Internal
    @ToBeReplacedByLazyProperty
    TestLogging getDebug();

    /**
     * Sets logging options for debug level.
     *
     * @param logging logging options for debug level
     * @since 1.1
     */
    void setDebug(TestLogging logging);

    /**
     * Configures logging options for debug level.
     *
     * @param action logging options for debug level
     * @since 1.1
     */
    void debug(Action<TestLogging> action);

    /**
     * Gets logging options for info level.
     *
     * @return logging options for info level
     * @since 1.1
     */
    @Internal
    @ToBeReplacedByLazyProperty
    TestLogging getInfo();

    /**
     * Sets logging options for info level.
     *
     * @param logging logging options for info level
     * @since 1.1
     */
    void setInfo(TestLogging logging);

    /**
     * Configures logging options for info level.
     *
     * @param action logging options for info level
     * @since 1.1
     */
    void info(Action<TestLogging> action);

    /**
     * Returns logging options for lifecycle level.
     *
     * @return logging options for lifecycle level
     * @since 1.1
     */
    @Internal
    @ToBeReplacedByLazyProperty
    TestLogging getLifecycle();

    /**
     * Sets logging options for lifecycle level.
     *
     * @param logging logging options for lifecycle level
     * @since 1.1
     */
    void setLifecycle(TestLogging logging);

    /**
     * Configures logging options for lifecycle level.
     *
     * @param action logging options for lifecycle level
     * @since 1.1
     */
    void lifecycle(Action<TestLogging> action);

    /**
     * Gets logging options for warn level.
     *
     * @return logging options for warn level
     * @since 1.1
     */
    @Internal
    @ToBeReplacedByLazyProperty
    TestLogging getWarn();

    /**
     * Sets logging options for warn level.
     *
     * @param logging logging options for warn level
     * @since 1.1
     */
    void setWarn(TestLogging logging);

    /**
     * Configures logging options for warn level.
     *
     * @param action logging options for warn level
     * @since 1.1
     */
    void warn(Action<TestLogging> action);

    /**
     * Returns logging options for quiet level.
     *
     * @return logging options for quiet level
     * @since 1.1
     */
    @Internal
    @ToBeReplacedByLazyProperty
    TestLogging getQuiet();

    /**
     * Sets logging options for quiet level.
     *
     * @param logging logging options for quiet level
     * @since 1.1
     */
    void setQuiet(TestLogging logging);

    /**
     * Configures logging options for quiet level.
     *
     * @param action logging options for quiet level
     * @since 1.1
     */
    void quiet(Action<TestLogging> action);

    /**
     * Returns logging options for error level.
     *
     * @return logging options for error level
     * @since 1.1
     */
    @Internal
    @ToBeReplacedByLazyProperty
    TestLogging getError();

    /**
     * Sets logging options for error level.
     *
     * @param logging logging options for error level
     * @since 1.1
     */
    void setError(TestLogging logging);

    /**
     * Configures logging options for error level.
     *
     * @param action logging options for error level
     * @since 1.1
     */
    void error(Action<TestLogging> action);

    /**
     * Returns logging options for the specified level.
     *
     * @param level the level whose logging options are to be returned
     *
     * @return logging options for the specified level
     * @since 1.1
     */
    TestLogging get(LogLevel level);
}
