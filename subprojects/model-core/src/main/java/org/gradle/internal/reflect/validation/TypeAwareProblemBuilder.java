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

import org.gradle.api.problems.interfaces.DocLink;
import org.gradle.api.problems.interfaces.Problem;
import org.gradle.api.problems.interfaces.ProblemBuilder;
import org.gradle.api.problems.interfaces.Severity;

import javax.annotation.Nullable;

public class TypeAwareProblemBuilder implements ProblemBuilder {

    private final ProblemBuilder delegate;

    public TypeAwareProblemBuilder(ProblemBuilder delegate) {
        this.delegate = delegate;
    }

    @Override
    public ProblemBuilder message(String message) {
        return delegate.message(message);
    }

    @Override
    public ProblemBuilder severity(Severity severity) {
        return delegate.severity(severity);
    }

    @Override
    public ProblemBuilder location(String path, Integer line) {
        return delegate.location(path, line);
    }

    @Override
    public ProblemBuilder noLocation() {
        return delegate.noLocation();
    }

    @Override
    public ProblemBuilder description(String description) {
        return delegate.description(description);
    }

    @Override
    public ProblemBuilder documentedAt(DocLink doc) {
        return delegate.documentedAt(doc);
    }

    @Override
    public ProblemBuilder undocumented() {
        return delegate.undocumented();
    }

    @Override
    public ProblemBuilder type(String problemType) {
        return delegate.type(problemType);
    }

    @Override
    public ProblemBuilder solution(@Nullable String solution) {
        return delegate.solution(solution);
    }

    @Override
    public ProblemBuilder cause(Throwable cause) {
        return delegate.cause(cause);
    }

    @Override
    public ProblemBuilder withMetadata(String key, String value) {
        return delegate.withMetadata(key, value);
    }

    @Override
    public Problem build() {
        return delegate.build();
    }

    @Override
    public void report() {
        delegate.report();
    }

    @Override
    public RuntimeException throwIt() {
        return delegate.throwIt();
    }

    public TypeAwareProblemBuilder withAnnotationType(Class<?> classWithAnnotationAttached) { // TODO (donat) figure out how all functions can return TypeAwareProblemBuilder
        delegate.withMetadata("typeName", classWithAnnotationAttached.getName().replaceAll("\\$", "."));
        return this;
    }
}
