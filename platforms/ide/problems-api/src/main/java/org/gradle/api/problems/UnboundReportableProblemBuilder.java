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
 * Problem builder creating reportable problems without enforcing any particular invocation order.
 *
 * @since 8.6
 */
@Incubating
public interface UnboundReportableProblemBuilder extends UnboundProblemBuilder, UnboundBasicProblemBuilder, ReportableProblemBuilder {
    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder documentedAt(DocLink doc);

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder undocumented();

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder fileLocation(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer length);

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder pluginLocation(String pluginId);

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder stackLocation();

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder noLocation();

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder label(String label, Object... args);

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder category(String category, String... details);

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder details(String details);

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder solution(String solution);

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder additionalData(String key, Object value);

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder withException(RuntimeException e);

    /**
     * {@inheritDoc}
     */
    @Override
    UnboundReportableProblemBuilder severity(Severity severity);

    /**
     * {@inheritDoc}
     */
    @Override
    ReportableProblem build();
}
