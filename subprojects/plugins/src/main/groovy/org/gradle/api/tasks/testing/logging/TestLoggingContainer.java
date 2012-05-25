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

import groovy.lang.Closure;

/**
 * Container for all test logging related options. Different options
 * can be set for each log level. Options that are set directly (without
 * specifying a log level) apply to log level LIFECYCLE.
 */
public interface TestLoggingContainer extends TestLogging {
    TestLogging getDebug();

    void setDebug(TestLogging logging);

    void debug(Action<TestLogging> action);

    void debug(Closure<?> closure);

    TestLogging getInfo();

    void setInfo(TestLogging logging);

    void info(Action<TestLogging> action);

    void info(Closure<?> closure);

    TestLogging getLifecycle();

    void setLifecycle(TestLogging logging);

    void lifecycle(Action<TestLogging> action);

    void lifecycle(Closure<?> closure);

    TestLogging getWarn();

    void setWarn(TestLogging logging);

    void warn(Action<TestLogging> action);

    void warn(Closure<?> closure);

    TestLogging getQuiet();

    void setQuiet(TestLogging logging);

    void quiet(Action<TestLogging> action);

    void quiet(Closure<?> closure);

    TestLogging getError();

    void setError(TestLogging logging);

    void error(Action<TestLogging> action);

    void error(Closure<?> closure);

    TestLogging get(LogLevel level);
}
