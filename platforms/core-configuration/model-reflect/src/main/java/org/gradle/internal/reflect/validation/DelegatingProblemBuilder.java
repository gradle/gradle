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

import org.gradle.api.Action;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.AdditionalDataSpec;
import org.gradle.api.problems.internal.ProblemInternal;
import org.gradle.api.problems.internal.ProblemBuilderInternal;
import org.gradle.api.problems.internal.ProblemSpecInternal;
import org.gradle.api.problems.internal.ProblemsInfrastructure;
import org.gradle.problems.ProblemDiagnostics;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class DelegatingProblemBuilder implements ProblemBuilderInternal {

    private final ProblemBuilderInternal delegate;

    DelegatingProblemBuilder(ProblemBuilderInternal delegate) {
        this.delegate = delegate;
    }

    @Override
    public ProblemInternal build() {
        return delegate.build();
    }

    @Override
    public ProblemBuilderInternal id(ProblemId problemId) {
        return validateDelegate(delegate).id(problemId);
    }

    @Override
    public ProblemBuilderInternal id(String name, String displayName, ProblemGroup parent) {
        return validateDelegate(delegate).id(name, displayName, parent);
    }

    @Override
    public ProblemBuilderInternal contextualLabel(String contextualLabel) {
        return validateDelegate(delegate).contextualLabel(contextualLabel);
    }

    @Override
    public ProblemBuilderInternal documentedAt(DocLink doc) {
        return validateDelegate(delegate.documentedAt(doc));
    }

    @Override
    public ProblemBuilderInternal documentedAt(String url) {
        return validateDelegate(delegate.documentedAt(url));
    }

    @Override
    public ProblemBuilderInternal fileLocation(String path) {
        return validateDelegate(delegate.fileLocation(path));
    }

    @Override
    public ProblemBuilderInternal lineInFileLocation(String path, int line) {
        return validateDelegate(delegate.lineInFileLocation(path, line));
    }

    @Override
    public ProblemBuilderInternal lineInFileLocation(String path, int line, int column) {
        return validateDelegate(delegate.offsetInFileLocation(path, line, column));
    }

    @Override
    public ProblemBuilderInternal lineInFileLocation(String path, int line, int column, int length) {
        return validateDelegate(delegate.lineInFileLocation(path, line, column, length));
    }

    @Override
    public ProblemBuilderInternal offsetInFileLocation(String path, int offset, int length) {
        return validateDelegate(delegate.offsetInFileLocation(path, offset, length));
    }

    @Override
    public ProblemBuilderInternal stackLocation() {
        return validateDelegate(delegate.stackLocation());
    }

    @Override
    public ProblemBuilderInternal details(String details) {
        return validateDelegate(delegate.details(details));
    }

    @Override
    public ProblemBuilderInternal solution(@Nullable String solution) {
        return validateDelegate(delegate.solution(solution));
    }

    @Override
    public ProblemBuilderInternal taskLocation(String buildTreePath) {
        return validateDelegate(delegate.taskLocation(buildTreePath));
    }

    @Override
    public <U extends AdditionalDataSpec> ProblemBuilderInternal additionalDataInternal(Class<? extends U> specType, Action<? super U> config) {
        return validateDelegate(delegate.additionalDataInternal(specType, config));
    }

    @Override
    public <T extends AdditionalData> ProblemBuilderInternal additionalData(Class<T> type, Action<? super T> config) {
        return validateDelegate(delegate.additionalData(type, config));
    }

    @Override
    public ProblemBuilderInternal withException(Throwable t) {
        return validateDelegate(delegate.withException(t));
    }

    @Override
    public ProblemBuilderInternal severity(Severity severity) {
        return validateDelegate(delegate.severity(severity));
    }

    @Override
    public ProblemsInfrastructure getInfrastructure() {
        return delegate.getInfrastructure();
    }

    @Override
    public ProblemSpecInternal diagnostics(ProblemDiagnostics diagnostics) {
        return delegate.diagnostics(diagnostics);
    }

    private <T> T validateDelegate(T newDelegate) {
        if (delegate != newDelegate) {
            throw new IllegalStateException("Builder pattern expected to return 'this'");
        }
        return newDelegate;
    }
}
