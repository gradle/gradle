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
 * Defines different ways to report problems.
 *
 * @since 8.6
 */
@Incubating
public interface ProblemReporter {

    /**
     * Configures and reports a new problem.
     * <p>
     * The spec must specify the problem label and the category. Any additional configuration is optional.
     *
     * @param spec the problem configuration
     * @since 8.6
     */
    void reporting(Action<ProblemSpec> spec);

    /**
     * Configures a new problem, reports it, and uses it to throw a new exception.
     * <p>
     * An exception must be provided in the spec.
     * <p>
     * The spec must specify the exception, the problem label, and the category. Any additional configuration is optional.
     *
     * @return nothing, the method throws an exception
     * @since 8.6
     */
    RuntimeException throwing(Action<ProblemSpec> spec);

    /**
     * Configures a new problem using an existing exception as input, reports it, and uses it to throw a new exception.
     * <p>
     * The spec must specify the problem label and the category. Any additional configuration is optional.
     *
     * @return nothing, the method throws an exception
     * @since 8.6
     */
    RuntimeException rethrowing(RuntimeException e, Action<ProblemSpec> spec);
}
