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
     * Defines simple identification for this problem.
     * <p>
     * It is a mandatory property to configure when emitting a problem with {@link ProblemReporter}..
     * <p>
     * Calling this method will set the reported problem group to {@link SharedProblemGroup#generic()}
     *
     * @param name the name of the problem. As a convention kebab-case-formatting should be used.
     * @param displayName a human-readable representation of the problem, free of any contextual information.
     * @return this
     * @since 8.8
     */
    ProblemSpec id(String name, String displayName);

    /**
     * Defines simple identification for this problem.
     * <p>
     * It is a mandatory property to configure when emitting a problem with {@link ProblemReporter}.
     *
     * @param name the name of the problem. As a convention kebab-case-formatting should be used.
     * @param displayName a human-readable representation of the problem, free of any contextual information.
     * @param parent the container problem group.
     * @return this
     * @since 8.8
     */
    ProblemSpec id(String name, String displayName, ProblemGroup parent);

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

    /**
     * Attaches additional data describing the problem.
     * <p>
     * Only the types listed for {@link AdditionalData} can be used as arguments, otherwise an invalid problem report will be created.
     * <p>
     * If not additional data was configured for this problem, then a new instance will be created. If additional data was already configured, then the existing instance will be used and the configuration will be applied to it.
     *
     * @param specType the type of the additional data configurer (see the AdditionalDataSpec interface for the list of supported types)
     * @param config  The action configuring the additional data
     * @return this
     * @param <U> The type of the configurator object that will be applied to the additional data
     * @since 8.12
     */
    <U extends AdditionalDataSpec> ProblemSpec additionalData(Class<? extends U> specType, Action<? super U> config);
}
