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
    void setEvents(Iterable<?> events);

    /**
     * Sets the events to be logged. Events can be passed as enum values (e.g. {@link TestLogEvent#FAILED}) or Strings (e.g. "failed").
     *
     * @param events the events to be logged
     */
    void events(Object... events);

    /**
     * Returns the minimum granularity of the events to be logged. Typically, 0 corresponds to the Gradle-generated test suite for the whole test run, 1 corresponds to the Gradle-generated test suite
     * for a particular test JVM, 2 corresponds to a test class, and 3 corresponds to a test method. These values will vary if user-defined suites are executed. <p>-1 denotes the highest granularity
     * and corresponds to an atomic test.
     *
     * @return the minimum granularity of the events to be logged
     */
    int getMinGranularity();

    /**
     * Sets the minimum granularity of the events to be logged. Typically, 0 corresponds to the Gradle-generated test suite for the whole test run, 1 corresponds to the Gradle-generated test suite for
     * a particular test JVM, 2 corresponds to a test class, and 3 corresponds to a test method. These values will vary if user-defined suites are executed. <p>-1 denotes the highest granularity and
     * corresponds to an atomic test.
     *
     * @param granularity the minimum granularity of the events to be logged
     */
    void setMinGranularity(int granularity);

    /**
     * Returns the maximum granularity of the events to be logged. Typically, 0 corresponds to the Gradle-generated test suite for the whole test run, 1 corresponds to the Gradle-generated test suite
     * for a particular test JVM, 2 corresponds to a test class, and 3 corresponds to a test method. These values will vary if user-defined suites are executed. <p>-1 denotes the highest granularity
     * and corresponds to an atomic test.
     *
     * @return the maximum granularity of the events to be logged
     */
    int getMaxGranularity();

    /**
     * Returns the maximum granularity of the events to be logged. Typically, 0 corresponds to the Gradle-generated test suite for the whole test run, 1 corresponds to the Gradle-generated test suite
     * for a particular test JVM, 2 corresponds to a test class, and 3 corresponds to a test method. These values will vary if user-defined suites are executed. <p>-1 denotes the highest granularity
     * and corresponds to an atomic test.
     *
     * @param granularity the maximum granularity of the events to be logged
     */
    void setMaxGranularity(int granularity);

    /**
     * Returns the display granularity of the events to be logged. For example, if set to 0, a method-level event will be displayed as "Test Run > Test Worker x > org.SomeClass > org.someMethod". If
     * set to 2, the same event will be displayed as "org.someClass > org.someMethod". <p>-1 denotes the highest granularity and corresponds to an atomic test.
     *
     * @return the display granularity of the events to be logged
     */
    int getDisplayGranularity();

    /**
     * Sets the display granularity of the events to be logged. For example, if set to 0, a method-level event will be displayed as "Test Run > Test Worker x > org.SomeClass > org.someMethod". If set
     * to 2, the same event will be displayed as "org.someClass > org.someMethod". <p>-1 denotes the highest granularity and corresponds to an atomic test.
     *
     * @param granularity the display granularity of the events to be logged
     */

    void setDisplayGranularity(int granularity);

    /**
     * Tells whether exceptions that occur during test execution will be logged. Typically these exceptions coincide with a "failed" event.
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
     * Tells whether causes of exceptions that occur during test execution will be logged. Only relevant if {@code showExceptions} is {@code true}.
     *
     * @return whether causes of exceptions that occur during test execution will be logged
     */
    boolean getShowCauses();

    /**
     * Sets whether causes of exceptions that occur during test execution will be logged. Only relevant if {@code showExceptions} is {@code true}.
     *
     * @param flag whether causes of exceptions that occur during test execution will be logged
     */
    void setShowCauses(boolean flag);

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
    void setExceptionFormat(Object exceptionFormat);

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
    void setStackTraceFilters(Iterable<?> stackTraces);

    /**
     * Convenience method for {@link #setStackTraceFilters(java.lang.Iterable)}. Accepts both enum values and Strings.
     */
    void stackTraceFilters(Object... stackTraces);

    /**
     * Tells whether output on standard out and standard error will be logged. Equivalent to checking if both log events {@code TestLogEvent.STANDARD_OUT} and {@code TestLogEvent.STANDARD_ERROR} are
     * set.
     */
    boolean getShowStandardStreams();

    /**
     * Sets whether output on standard out and standard error will be logged. Equivalent to setting log events {@code TestLogEvent.STANDARD_OUT} and {@code TestLogEvent.STANDARD_ERROR}.
     */
    TestLogging setShowStandardStreams(boolean flag);
}
