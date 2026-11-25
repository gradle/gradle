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
import org.gradle.internal.instrumentation.api.annotations.LazyProperties;
import org.jspecify.annotations.NullMarked;

/**
 * The lazy properties behind the eager getters and setters of a {@link TestLoggingContainer} of a test task.
 *
 * <p>As {@link TestLoggingContainer} does, the properties of this object are those of the {@code LIFECYCLE} log level.
 * The other log levels have their own lazy properties.</p>
 *
 * @see org.gradle.api.tasks.testing.AbstractTestTaskInputs#getTestLogging()
 * @since 10.0.0
 */
@Incubating
@NullMarked
public interface TestLoggingContainerInputs extends TestLoggingInputs {

    /**
     * The lazy properties for the {@code DEBUG} log level.
     *
     * @see TestLoggingContainer#getDebug()
     * @since 10.0.0
     */
    @LazyProperties
    TestLoggingInputs getDebug();

    /**
     * The lazy properties for the {@code INFO} log level.
     *
     * @see TestLoggingContainer#getInfo()
     * @since 10.0.0
     */
    @LazyProperties
    TestLoggingInputs getInfo();

    /**
     * The lazy properties for the {@code LIFECYCLE} log level. This is this object itself.
     *
     * @see TestLoggingContainer#getLifecycle()
     * @since 10.0.0
     */
    default TestLoggingInputs getLifecycle() {
        return this;
    }

    /**
     * The lazy properties for the {@code WARN} log level.
     *
     * @see TestLoggingContainer#getWarn()
     * @since 10.0.0
     */
    @LazyProperties
    TestLoggingInputs getWarn();

    /**
     * The lazy properties for the {@code QUIET} log level.
     *
     * @see TestLoggingContainer#getQuiet()
     * @since 10.0.0
     */
    @LazyProperties
    TestLoggingInputs getQuiet();

    /**
     * The lazy properties for the {@code ERROR} log level.
     *
     * @see TestLoggingContainer#getError()
     * @since 10.0.0
     */
    @LazyProperties
    TestLoggingInputs getError();
}
