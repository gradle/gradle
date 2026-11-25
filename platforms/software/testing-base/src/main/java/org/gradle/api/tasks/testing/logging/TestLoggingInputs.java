/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.Incubating;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of a {@link TestLogging} of a test task.
 *
 * @see TestLoggingContainerInputs
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface TestLoggingInputs {

    /**
     * The events to be logged.
     *
     * @see TestLogging#getEvents()
     * @since 10.0.0
     */
    SetProperty<TestLogEvent> getEvents();

    /**
     * The minimum granularity of the events to be logged.
     *
     * @see TestLogging#getMinGranularity()
     * @since 10.0.0
     */
    Property<Integer> getMinGranularity();

    /**
     * The maximum granularity of the events to be logged.
     *
     * @see TestLogging#getMaxGranularity()
     * @since 10.0.0
     */
    Property<Integer> getMaxGranularity();

    /**
     * The display granularity of the events to be logged.
     *
     * @see TestLogging#getDisplayGranularity()
     * @since 10.0.0
     */
    Property<Integer> getDisplayGranularity();

    /**
     * Whether exceptions that occur during test execution are logged.
     *
     * @see TestLogging#getShowExceptions()
     * @since 10.0.0
     */
    Property<Boolean> getShowExceptions();

    /**
     * Whether causes of exceptions that occur during test execution are logged.
     *
     * @see TestLogging#getShowCauses()
     * @since 10.0.0
     */
    Property<Boolean> getShowCauses();

    /**
     * Whether stack traces of exceptions that occur during test execution are logged.
     *
     * @see TestLogging#getShowStackTraces()
     * @since 10.0.0
     */
    Property<Boolean> getShowStackTraces();

    /**
     * The format to be used for logging test exceptions.
     *
     * @see TestLogging#getExceptionFormat()
     * @since 10.0.0
     */
    Property<TestExceptionFormat> getExceptionFormat();

    /**
     * The set of filters to be used for sanitizing test stack traces.
     *
     * @see TestLogging#getStackTraceFilters()
     * @since 10.0.0
     */
    SetProperty<TestStackTraceFilter> getStackTraceFilters();
}
