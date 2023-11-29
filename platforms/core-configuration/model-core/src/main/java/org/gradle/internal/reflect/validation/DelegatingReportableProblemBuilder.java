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

package org.gradle.internal.reflect.validation;

import org.gradle.api.NonNullApi;
import org.gradle.api.problems.BasicProblemBuilder;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.ProblemBuilderDefiningCategory;
import org.gradle.api.problems.ProblemBuilderDefiningDocumentation;
import org.gradle.api.problems.ProblemBuilderDefiningLabel;
import org.gradle.api.problems.ProblemBuilderDefiningLocation;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.api.problems.ReportableProblemBuilder;
import org.gradle.api.problems.Severity;

import javax.annotation.Nullable;

@NonNullApi
class DelegatingReportableProblemBuilder implements
    ProblemBuilderDefiningLabel,
    ProblemBuilderDefiningDocumentation,
    ProblemBuilderDefiningLocation,
    ProblemBuilderDefiningCategory,
    BasicProblemBuilder {

    private final ProblemBuilderDefiningLabel delegate;

    DelegatingReportableProblemBuilder(ProblemBuilderDefiningLabel delegate) {
        this.delegate = delegate;
    }

    @Override
    public ProblemBuilderDefiningDocumentation label(String label, Object... args) {
        return validateDelegate(delegate).label(label, args);
    }

    @Override
    public ProblemBuilderDefiningLocation documentedAt(DocLink doc) {
        return validateDelegate(((ProblemBuilderDefiningDocumentation) delegate).documentedAt(doc));
    }

    @Override
    public ProblemBuilderDefiningLocation undocumented() {
        return validateDelegate(((ProblemBuilderDefiningDocumentation) delegate).undocumented());
    }

    @Override
    public ProblemBuilderDefiningCategory fileLocation(String path, @Nullable Integer line, @Nullable Integer column, @Nullable Integer length) {
        return validateDelegate(((ProblemBuilderDefiningLocation) delegate).fileLocation(path, line, column, length));
    }

    @Override
    public ProblemBuilderDefiningCategory pluginLocation(String pluginId) {
        return validateDelegate(((ProblemBuilderDefiningLocation) delegate).pluginLocation(pluginId));
    }

    private <T> T validateDelegate(T newDelegate) {
        if (delegate != newDelegate) {
            throw new IllegalStateException("Builder pattern expected to return 'this'");
        }
        return newDelegate;
    }

    @Override
    public ProblemBuilderDefiningCategory stackLocation() {
        return validateDelegate(((ProblemBuilderDefiningLocation) delegate).stackLocation());
    }

    @Override
    public ProblemBuilderDefiningCategory noLocation() {
        return validateDelegate(((ProblemBuilderDefiningLocation) delegate).noLocation());
    }

    @Override
    public ProblemBuilder category(String category, String... details){
        return validateDelegate(((ProblemBuilderDefiningCategory) delegate).category(category, details));
    }

    @Override
    public ProblemBuilder details(String details) {
        return validateDelegate(((BasicProblemBuilder) delegate).details(details));
    }

    @Override
    public ReportableProblemBuilder solution(@Nullable String solution) {
        return (ReportableProblemBuilder) validateDelegate(((BasicProblemBuilder) delegate).solution(solution));
    }

    @Override
    public ReportableProblemBuilder additionalData(String key, Object value) {
        return (ReportableProblemBuilder) validateDelegate(((BasicProblemBuilder) delegate).additionalData(key, value));
    }

    @Override
    public ReportableProblemBuilder withException(RuntimeException e) {
        return (ReportableProblemBuilder) validateDelegate(((BasicProblemBuilder) delegate).withException(e));
    }

    @Override
    public ReportableProblemBuilder severity(@Nullable Severity severity) {
        ProblemBuilder newDelegate = ((ReportableProblemBuilder) delegate).severity(severity);
        return (ReportableProblemBuilder) validateDelegate(newDelegate);
    }

    @Override
    public ReportableProblem build() {
        return ((ReportableProblemBuilder) delegate).build();
    }
}
