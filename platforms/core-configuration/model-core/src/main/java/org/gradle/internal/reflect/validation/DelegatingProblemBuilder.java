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
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.AdditionalDataBuilderFactory;
import org.gradle.api.problems.internal.AdditionalDataSpec;
import org.gradle.api.problems.internal.InternalProblem;
import org.gradle.api.problems.internal.InternalProblemBuilder;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;

import javax.annotation.Nullable;

@NonNullApi
class DelegatingProblemBuilder implements InternalProblemBuilder {

    private final InternalProblemBuilder delegate;

    DelegatingProblemBuilder(InternalProblemBuilder delegate) {
        this.delegate = delegate;
    }

    @Override
    public InternalProblem build() {
        return delegate.build();
    }

    @Override
    public InternalProblemBuilder id(ProblemId problemId) {
        return validateDelegate(delegate).id(problemId);
    }

    @Override
    public InternalProblemBuilder id(String name, String displayName, ProblemGroup parent) {
        return validateDelegate(delegate).id(name, displayName, parent);
    }

    @Override
    public InternalProblemBuilder contextualLabel(String contextualLabel) {
        return validateDelegate(delegate).contextualLabel(contextualLabel);
    }

    @Override
    public InternalProblemBuilder documentedAt(DocLink doc) {
        return validateDelegate(delegate.documentedAt(doc));
    }

    @Override
    public InternalProblemBuilder documentedAt(String url) {
        return validateDelegate(delegate.documentedAt(url));
    }

    @Override
    public InternalProblemBuilder fileLocation(String path) {
        return validateDelegate(delegate.fileLocation(path));
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line) {
        return validateDelegate(delegate.lineInFileLocation(path, line));
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line, int column) {
        return validateDelegate(delegate.offsetInFileLocation(path, line, column));
    }

    @Override
    public InternalProblemBuilder lineInFileLocation(String path, int line, int column, int length) {
        return validateDelegate(delegate.lineInFileLocation(path, line, column, length));
    }

    @Override
    public InternalProblemBuilder offsetInFileLocation(String path, int offset, int length) {
        return validateDelegate(delegate.offsetInFileLocation(path, offset, length));
    }

    @Override
    public InternalProblemBuilder stackLocation() {
        return validateDelegate(delegate.stackLocation());
    }

    @Override
    public InternalProblemBuilder details(String details) {
        return validateDelegate(delegate.details(details));
    }

    @Override
    public InternalProblemBuilder solution(@Nullable String solution) {
        return validateDelegate(delegate.solution(solution));
    }

    @Override
    public InternalProblemBuilder taskPathLocation(String buildTreePath) {
        return validateDelegate(delegate.solution(buildTreePath));
    }

    @Override
    public <U extends AdditionalDataSpec> InternalProblemBuilder additionalDataInternal(Class<? extends U> specType, Action<? super U> config) {
        return validateDelegate(delegate.additionalDataInternal(specType, config));
    }

    @Override
    public <T extends AdditionalData> InternalProblemBuilder additionalData(Class<T> type, Action<? super T> config) {
        return validateDelegate(delegate.additionalData(type, config));
    }

    @Override
    public <T extends AdditionalData> InternalProblemBuilder additionalDataInternal(T additionalDataInstance) {
        return validateDelegate(delegate.additionalDataInternal(additionalDataInstance));
    }

    @Override
    public InternalProblemBuilder withException(Throwable t) {
        return validateDelegate(delegate.withException(t));
    }

    @Override
    public InternalProblemBuilder severity(Severity severity) {
        return validateDelegate(delegate.severity(severity));
    }

    @Override
    public AdditionalDataBuilderFactory getAdditionalDataBuilderFactory() {
        return delegate.getAdditionalDataBuilderFactory();
    }

    @Override
    public Instantiator getInstantiator() {
        return delegate.getInstantiator();
    }

    @Override
    public PayloadSerializer getPayloadSerializer() {
        return delegate.getPayloadSerializer();
    }

    private <T> T validateDelegate(T newDelegate) {
        if (delegate != newDelegate) {
            throw new IllegalStateException("Builder pattern expected to return 'this'");
        }
        return newDelegate;
    }
}
