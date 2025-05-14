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

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Provides options to configure problems.
 *
 * @see ProblemReporter
 * @since 8.6
 */
@Incubating
public interface ProblemSpec {
    /**
     * Declares a short, but context-dependent message for this problem.
     *
     * @param contextualLabel the short message
     * @return this
     * @since 8.8
     */
    ProblemSpec contextualLabel(String contextualLabel);

    /**
     * Declares where this problem is documented.
     *
     * @return this
     * @since 8.6
     */
    ProblemSpec documentedAt(String url);

    /**
     * Declares that this problem is in a file.
     *
     * @param path the file location
     * @return this
     * @since 8.6
     */
    ProblemSpec fileLocation(String path);

    /**
     * Declares that this problem is in a file on a line.
     *
     * @param path the file location
     * @param line the one-indexed line number
     * @return this
     * @since 8.6
     */
    ProblemSpec lineInFileLocation(String path, int line);

    /**
     * Declares that this problem is in a file with on a line at a certain position.
     *
     * @param path the file location
     * @param line the one-indexed line number
     * @param column the one-indexed column
     * @return this
     * @since 8.6
     */
    ProblemSpec lineInFileLocation(String path, int line, int column);

    /**
     * Declares that this problem is in a file with on a line at a certain position.
     *
     * @param path the file location
     * @param line the one-indexed line number
     * @param column the one-indexed column
     * @param length the length of the text
     * @return this
     * @since 8.6
     */
    ProblemSpec lineInFileLocation(String path, int line, int column, int length);

    /**
     * Declares that this problem is in a file at a certain global position with a given length.
     *
     * @param path the file location
     * @param offset the zero-indexed global offset from the beginning of the file
     * @param length the length of the text
     * @return this
     * @since 8.6
     */
    ProblemSpec offsetInFileLocation(String path, int offset, int length);

    /**
     * Declares that this problem is at the same place where it's reported. The stack trace will be used to determine the location.
     *
     * @return this
     * @since 8.6
     */
    ProblemSpec stackLocation();

    /**
      Declares a long description detailing the problem.
     * <p>
     * Details can elaborate on the problem, and provide more information about the problem.
     * They can be multiple lines long, but should not detail solutions; for that, use {@link #solution(String)}.
     *
     * @param details the details
     * @return this
     * @since 8.6
     */
    ProblemSpec details(String details);

    /**
     * Declares solutions and advice that contain context-sensitive data, e.g. the message contains references to variables, locations, etc.
     *
     * @param solution the solution.
     * @return this
     * @since 8.6
     */
    ProblemSpec solution(String solution);

    /**
     * Declares additional data attached to the problem.
     *
     * @param type The type of the additional data.
     * This can be any type that implements {@link AdditionalData} including {@code abstract} classes and interfaces.
     * This type will be instantiated and provided as an argument for the {@code Action} passed as the second argument.
     * <p>
     * The type can have the following properties:
     * <ul>
     *     <li>getters and setters for collections, simple types and other types that itself follow these restrictions
     *         <ul>
     *            <li>simple types: {@link String}, {@link Integer}, {@link Boolean}, etc.</li>
     *            <li>collections: {@link java.util.List}, {@link java.util.Set}, {@link java.util.Map}</li>
     *            <li>primitives: {@code int}, {@code boolean}, etc.</li>
     *         </ul>
     *     </li>
     *     <li>Provider API types
     *       <ul>
     *           <li>{@link org.gradle.api.provider.Property}</li>
     *           <li>{@link org.gradle.api.provider.ListProperty}</li>
     *           <li>{@link org.gradle.api.provider.SetProperty}</li>
     *           <li>{@link org.gradle.api.provider.MapProperty}</li>
     *       </ul>
     *     </li>
     * </ul>
     *
     * @param config The configuration action for the additional data.
     *
     * @return this
     * @since 8.13
     */
    <T extends AdditionalData> ProblemSpec additionalData(Class<T> type, Action<? super T> config);

    /**
     * Declares the exception causing this problem.
     *
     * @param t the exception.
     * @return this
     * @since 8.11
     */
    ProblemSpec withException(Throwable t);

    /**
     * Declares the severity of the problem.
     *
     * @param severity the severity
     * @return this
     * @since 8.6
     */
    ProblemSpec severity(Severity severity);
}
