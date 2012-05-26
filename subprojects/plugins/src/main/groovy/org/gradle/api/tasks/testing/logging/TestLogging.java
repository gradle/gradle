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
 * Options that determine which test events get logged, and at which detail.
 */
public interface TestLogging {
    /**
     * Returns the events to be logged.
     *
     * @return the events to be logged
     */
    Set<TestLogEvent> getEvents();

    /**
     * Sets the events to be logged.
     *
     * @param events the events to be logged
     */
    void setEvents(Set<TestLogEvent> events);

    /**
     * Sets the events to be logged. Events can be passed as enum values
     * (e.g. {@link TestLogEvent#FAILED}) or Strings (e.g. "failed").
     *
     * @param events the events to be logged
     */
    void events(Object... events);

    /**
     * Returns the minimum granularity of the events to be logged. In a typical
     * JUnit setup, 0 corresponds to the overall test suite, 1 corresponds to
     * the test suite of a particular test JVM, 2 corresponds to a test class,
     * and 3 corresponds to a test method. These values will vary if user-defined
     * suites are executed.
     * <p>-1 denotes the highest granularity, and will cause only atomic tests
     * (test methods in above example) to be logged.
     *
     * @return the minimum granularity of the events to be logged
     */
    int getMinGranularity();

    /**
     * Sets the minimum granularity of the events to be logged. In a typical
     * JUnit setup, 0 corresponds to the overall test suite, 1 corresponds to
     * the test suite of a particular test JVM, 2 corresponds to a test class,
     * and 3 corresponds to a test method. These values will vary if user-defined
     * suites are executed.
     * <p>-1 denotes the highest granularity, and will cause only atomic tests
     * (test methods in above example) to be logged.
     *
     * @param granularity the minimum granularity of the events to be logged
     */
    void setMinGranularity(int granularity);

    /**
     * Convenience method that delegates to {@link #setMinGranularity(int)}.
     */
    void minGranularity(int granularity);

    /**
     * Returns the maximum granularity of the events to be logged. See {@link #getMinGranularity()}
     * for further details.
     *
     * @return the maximum granularity of the events to be logged
     */
    int getMaxGranularity();

    /**
     * Sets the maximum granularity of the events to be logged. See {@link #setMinGranularity(int)}
     * for further details.
     *
     * @param granularity the maximum granularity of the events to be logged
     */
    void setMaxGranularity(int granularity);

    /**
     * Convenience method that delegates to {@link #setMaxGranularity(int)}.
     */
    void maxGranularity(int granularity);

    /**
     * Tells whether exceptions that occur during test execution will be logged.
     * Typically these exceptions coincide with a "failed" event.
     *
     * @return whether exceptions that occur during test execution will be logged
     */
    boolean getShowExceptions();

    /**
     * Sets whether exceptions that occur during test execution will be logged.
     *
     * @param flag whether exceptions that occur during test execution will be logged
     */
    void setShowExceptions(boolean flag);

    /**
     * Convenience method for {@link #setShowExceptions(boolean)}.
     */
    void showExceptions(boolean flag);

    /**
     * Tells whether causes of exceptions that occur during test execution will be logged.
     * Only relevant if {@code showExceptions} is {@code true}.
     *
     * @return whether causes of exceptions that occur during test execution will be logged
     */
    boolean getShowCauses();

    /**
     * Sets whether causes of exceptions that occur during test execution will be logged.
     * Only relevant if {@code showExceptions} is {@code true}.
     *
     * @param flag whether causes of exceptions that occur during test execution will be logged
     */
    void setShowCauses(boolean flag);

    /**
     * Convenience method for {@link #setShowCauses(boolean)}.
     */
    void showCauses(boolean flag);

    /**
     * Tells whether stack traces of exceptions that occur during test execution will be logged.
     *
     * @return whether stack traces of exceptions that occur during test execution will be logged
     */
    boolean getShowStackTraces();

    /**
     * Sets whether stack traces of exceptions that occur during test execution will be logged.
     *
     * @param flag whether stack traces of exceptions that occur during test execution will be logged
     */
    void setShowStackTraces(boolean flag);

    /**
     * Convenience method for {@link #setShowStackTraces(boolean)}.
     */
    void showStackTraces(boolean flag);

    /**
     * Returns the format to be used for logging test exceptions. Only relevant if {@code showStackTraces} is {@code true}.
     *
     * @return the format to be used for logging test exceptions
     */
    TestExceptionFormat getExceptionFormat();

    /**
     * Sets the format to be used for logging test exceptions. Only relevant if {@code showStackTraces} is {@code true}.
     *
     * @param exceptionFormat the format to be used for logging test exceptions
     */
    void setExceptionFormat(TestExceptionFormat exceptionFormat);

    /**
     * Convenience method for {@link #setExceptionFormat(TestExceptionFormat)}. Accepts both enum values and Strings.
     */
    void exceptionFormat(Object exceptionFormat);

    /**
     * Returns the set of filters to be used for sanitizing test stack traces.
     *
     * @return the set of filters to be used for sanitizing test stack traces
     */
    Set<TestStackTraceFilter> getStackTraceFilters();

    /**
     * Sets the set of filters to be used for sanitizing test stack traces.
     *
     * @param stackTraces the set of filters to be used for sanitizing test stack traces
     */
    void setStackTraceFilters(Set<TestStackTraceFilter> stackTraces);

    /**
     * Convenience method for {@link #setStackTraceFilters(java.util.Set)}. Accepts both enum values and Strings.
     */
    void stackTraceFilters(Object... stackTraces);

    /**
     * Tells whether log events {@link TestLogEvent#STANDARD_OUT}
     * and {@link TestLogEvent#STANDARD_ERROR} will be logged.
     */
     boolean getShowStandardStreams();

    /**
     * Adds log events {@link TestLogEvent#STANDARD_OUT}
     * and {@link TestLogEvent#STANDARD_ERROR}.
     */
     void setShowStandardStreams(boolean flag);

    /**
     * Convenience method for {@link #setShowStandardStreams(boolean)}.
     */
    void showStandardStreams(boolean flag);
}
