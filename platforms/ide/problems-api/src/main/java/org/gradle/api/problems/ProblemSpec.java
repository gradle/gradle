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

/**
 * Provides options to configure problems.
 * <p>
 *
 * @see ProblemReporter
 * @since 8.6
 */
@Incubating
public interface ProblemSpec {

    /**
     * Declares a short message for this problem.
     * <p>
     * The label is the main, human-readable representation of the problem.
     * It is a mandatory property to configure when emitting a problem with {@link ProblemReporter}.
     *
     * @param label the short message
     * @return this
     * @since 8.6
     */
    ProblemSpec label(String label);

    /**
     * A category groups related problems together.
     * <p>
     * Category is a mandatory property to configure when emitting a problem with {@link ProblemReporter}.
     * <p>
     * A category defines the following hierarchical elements to distinguish instances:
     * <ul>
     *     <li>namespace</li>
     *     <li>category</li>
     *     <li>subcategories</li>
     * </ul>
     * <p>
     * The namespace provides separation for identical problems emitted from different components.
     * Problems emitted from Gradle core will use the {@code org.gradle} namespace.
     * Third party plugins are expected to use their plugin id for namespace.
     * Problems emitted from build scripts should use the {@code buildscript} namespace.
     * The namespace is bound to {@link ProblemReporter}, hence it is absent from the argument list.
     * <p>
     * A category should contain the most broad term describing the problem.
     * A few examples are: {@code compilation}, {@code deprecation}, {@code task-validation}.
     * <p>
     * The problem category can be refined with an optional hierarchy of subcategories.
     * For example, a problem covering a java compilation warning can be denoted with the following subcategories: {@code [java, unused-variable]}.
     * <p>
     * The categorization depends on the domain and don't have any constraints. Clients (i.e. IDEs) receiving problems should use the category information for
     * properly group and sort the received instances.
     * However, we recommend to use the same conventions as the problems emitted from Gradle core use.
     * <ul>
     *     <li>Entries should be all-lowercase using a dash for separator (i.e. kebab-case)</li>
     *     <li>Should be strictly hierarchical: the category declares the domain and subcategories provide further refinement</li>
     * </ul>
     * A few examples with a path-like notation (i.e. {@code category:subcategory1:subcategory2}).
     * <ul>
     *     <li>compilation:groovy-dsl</li>
     *     <li>compilation:java:unused-import</li>
     *     <li>deprecation:user-code-direct</li>
     *     <li>task-selection:no-matches</li>
     * </ul>
     *
     * @param category the type name
     * @param subcategories the type subcategories
     * @return this
     * @since 8.6
     */
    ProblemSpec category(String category, String... subcategories);

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
     * <p>
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
     * A description of how to solve this problem.
     *
     * @param solution the solution.
     * @return this
     * @since 8.6
     */
    ProblemSpec solution(String solution);

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
