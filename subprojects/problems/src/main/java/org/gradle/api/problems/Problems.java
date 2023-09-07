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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

/**
 * Problems API service.
 * <p>
 * The main purpose of this API is to allow clients to create configure and report problems in a centralized way.
 * <p>
 * Reported problems are exposed via build operation progress events, which then be converted to Tooling API progress events.
 *
 * @since 8.4
 */
@Incubating
@ServiceScope(Scope.Global.class)
public interface Problems {

    /**
     * Returns a new problem builder which can configure and create Problem instances.
     * <p>
     * The builder uses a stepwise builder pattern forcing the clients to define all mandatory fields in a specific order.
     * <p>
     * Once all mandatory fields are set, the returned type will allow clients to call {@link ProblemBuilder#build()} to create a new Problem instance.
     * The {@link ProblemBuilder#build()} method doesn't have any side effects, it just creates a new instance. Problems should be reported separately with {@link ReportableProblem#report()}.
     *
     * @return a new problem builder
     */
    ProblemBuilderDefiningLabel createProblemBuilder();

    /**
     * Configures a new problem with error severity, reports it and uses it to throw a new exception.
     * <p>
     *
     * @return nothing, the method throws an exception
     */
    RuntimeException throwing(ProblemBuilderSpec action);

    /**
     * Configures a new problem with error severity using an existing exception as input, reports it and uses it to throw a new exception.
     * <p>
     *
     * @return nothing, the method throws an exception
     */
    RuntimeException rethrowing(RuntimeException e, ProblemBuilderSpec action);

    /**
     * Configures a new problem and reports it.
     * <p>
     *
     * @since 8.5
     */
    void report(ProblemBuilderSpec action);

    /**
     * Configures a new problem and reports it.
     * <p>
     *
     * @since 8.5
     */
    ReportableProblem createProblem(ProblemBuilderSpec action);
}
