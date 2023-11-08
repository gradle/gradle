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
 * {@link Problem} instance configurator allowing the specification of all optional fields.
 *
 * This is the last interface in the builder chain. The order of steps can be traced from the {@link Problems} service interface.
 *
 * An example of how to use the builder:
 * <pre>{@code
 *  <problemService>.report(configurator -> configurator
 *          .label("test problem")
 *          .undocumented()
 *          .noLocation()
 *          .cotegory("problemCategory")
 *          .severity(Severity.ERROR)
 *          .details("this is a test")
 *  }</pre>
 *
 * @since 8.5
 */
@Incubating
public interface ProblemBuilder {
    /**
     * The long description of this problem.
     *
     * @param details the details
     * @return this
     */
    ProblemBuilder details(String details);

    /**
     * The description of how to solve this problem
     *
     * @param solution the solution.
     * @return this
     */
    ProblemBuilder solution(String solution);

    /**
     * Specifies arbitrary data associated with this problem.
     * Currently supported value types:
     * <ul>
     *     <li>java.lang.String</li>
     * </ul>
     *
     * @return this
     * @throws RuntimeException for unsupported value types
     * @since 8.6
     */
    ProblemBuilder additionalData(String key, Object value);

    /**
     * The exception causing this problem.
     *
     * @param e the exception.
     * @return this
     */
    ProblemBuilder withException(RuntimeException e);

    /**
     * Declares the severity of the problem.
     *
     * @param severity the severity
     * @return this
     */
    ProblemBuilder severity(Severity severity);
}
