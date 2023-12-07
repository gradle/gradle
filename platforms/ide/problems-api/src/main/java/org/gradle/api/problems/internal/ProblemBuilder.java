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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Severity;

import javax.annotation.Nullable;


public interface ProblemBuilder extends ProblemSpec {

    /**
     * Creates the new problem. Calling this method won't report the problem via build operations, it can be done separately by calling {@link org.gradle.api.problems.internal.InternalProblemReporter#report(Problem)}.
     *
     * @return the new problem
     */
    Problem build();

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder label(String label);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder category(String category, String... details);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder documentedAt(DocLink doc);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder documentedAt(String url);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder fileLocation(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer length);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder pluginLocation(String pluginId);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder stackLocation();

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder details(String details);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder solution(String solution);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder additionalData(String key, Object value);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder withException(RuntimeException e);

    /**
     * {@inheritDoc}
     */
    @Override
    ProblemBuilder severity(Severity severity);
}
