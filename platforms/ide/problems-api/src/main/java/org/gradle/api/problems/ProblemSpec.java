/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

import javax.annotation.Nullable;

/**
 * Configures a {@link Problem}.
 *
 * @see ProblemReporter
 * @since 8.6
 */
@Incubating
public interface ProblemSpec {

    /**
     * Declares a short message for this problem.
     *
     * @param label the short message
     * @return this
     * @since 8.6
     */
    ProblemSpec label(String label);

    /**
     * Declares the problem category.
     *
     * @param category the type name
     * @param details the type details
     * @return this
     * @see ProblemCategory
     * @since 8.6
     */
    ProblemSpec category(String category, String... details);

    /**
     * Declares the documentation for this problem.
     *
     * @return this
     * @since 8.6
     */
    ProblemSpec documentedAt(DocLink doc);

    /**
     * Declares the documentation for this problem.
     *
     * @return this
     * @since 8.6
     */
    ProblemSpec documentedAt(String url);

    /**
     * Declares that this problem is in a file with optional position and length.
     *
     * @param path the file location
     * @param line the line number
     * @param column the column number
     * @param length the length of the text
     * @return this
     * @since 8.6
     */
    ProblemSpec fileLocation(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer length);

    /**
     * Declares that this problem is emitted while applying a plugin.
     *
     * @param pluginId the ID of the applied plugin
     * @return this
     * @since 8.6
     */
    ProblemSpec pluginLocation(String pluginId);

    /**
     * Declares that this problem should automatically collect the location information based on the current stack trace.
     *
     * @return this
     * @since 8.6
     */
    ProblemSpec stackLocation();

    /**
     * The long description of this problem.
     *
     * @param details the details
     * @return this
     * @since 8.6
     */
    ProblemSpec details(String details);

    /**
     * The description of how to solve this problem.
     *
     * @param solution the solution.
     * @return this
     * @since 8.6
     */
    ProblemSpec solution(String solution);

    /**
     * Specifies arbitrary data associated with this problem.
     * <p>
     * The only supported value type is {@link String}. Future Gradle versions may support additional types.
     *
     * @return this
     * @throws RuntimeException for null values and for values with unsupported type.
     * @since 8.6
     */
    ProblemSpec additionalData(String key, Object value);

    /**
     * The exception causing this problem.
     *
     * @param e the exception.
     * @return this
     * @since 8.6
     */
    ProblemSpec withException(RuntimeException e);

    /**
     * Declares the severity of the problem.
     *
     * @param severity the severity
     * @return this
     * @since 8.6
     */
    ProblemSpec severity(Severity severity);
}
