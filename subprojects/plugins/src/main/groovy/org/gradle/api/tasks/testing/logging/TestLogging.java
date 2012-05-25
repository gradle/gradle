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

import java.util.Set;

/**
 * Options for test logging.
 */
public interface TestLogging {
    Set<TestLogEvent> getEvents();

    void setEvents(Set<TestLogEvent> events);

    void events(Object... events);

    int getMinGranularity();

    void setMinGranularity(int granularity);

    void minGranularity(int granularity);

    int getMaxGranularity();

    void setMaxGranularity(int granularity);

    void maxGranularity(int granularity);

    boolean getShowExceptions();

    void setShowExceptions(boolean flag);

    void showExceptions(boolean flag);

    boolean getShowCauses();

    void setShowCauses(boolean flag);

    void showCauses(boolean flag);

    boolean getShowStackTraces();

    void setShowStackTraces(boolean flag);

    void showStackTraces(boolean flag);

    TestExceptionFormat getExceptionFormat();

    void setExceptionFormat(TestExceptionFormat exceptionFormat);

    void exceptionFormat(Object exceptionFormat);

    Set<TestStackTraceFilter> getStackTraceFilters();

    void setStackTraceFilters(Set<TestStackTraceFilter> stackTraces);

    void stackTraceFilters(Object... stackTraces);

    /**
     * Tells whether to log standard stream output. If {@code true}, standard output and standard error will be logged at some level.
     */
     boolean getShowStandardStreams();

    /**
     * Sets whether to log standard stream output. If {@code true}, standard output and standard error will be logged at level LIFECYCLE.
     */
     void setShowStandardStreams(boolean flag);
}
