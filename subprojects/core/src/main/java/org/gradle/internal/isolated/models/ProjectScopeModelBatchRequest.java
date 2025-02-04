/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.isolated.models;

import org.gradle.internal.Cast;

import java.util.Collections;
import java.util.List;

public class ProjectScopeModelBatchRequest<T> {
    public enum Kind {
        LENIENT, STRICT, ALL_VALUES, ALL_MODELS
    }

    private final ProjectModelScopeIdentifier consumer;
    private final IsolatedModelKey<T> key;
    private final List<ProjectModelScopeIdentifier> producers;
    private final Kind kind;

    public ProjectScopeModelBatchRequest(ProjectModelScopeIdentifier consumer, IsolatedModelKey<T> key, List<ProjectModelScopeIdentifier> producers, boolean lenient) {
        this(consumer, key, producers, lenient ? Kind.LENIENT : Kind.STRICT);
    }

    private ProjectScopeModelBatchRequest(ProjectModelScopeIdentifier consumer, IsolatedModelKey<T> key) {
        this(consumer, key, Collections.emptyList(), Kind.ALL_VALUES);
    }

    private ProjectScopeModelBatchRequest(ProjectModelScopeIdentifier consumer) {
        this(consumer, Cast.uncheckedNonnullCast(IsolatedModelKey.ANY), Collections.emptyList(), Kind.ALL_MODELS);
    }

    public ProjectScopeModelBatchRequest(ProjectModelScopeIdentifier consumer, IsolatedModelKey<T> key, List<ProjectModelScopeIdentifier> producers, Kind kind) {
        this.consumer = consumer;
        this.key = key;
        this.producers = producers;
        this.kind = kind;
    }

    public ProjectModelScopeIdentifier getConsumer() {
        return consumer;
    }

    public IsolatedModelKey<T> getModelKey() {
        return key;
    }

    public List<ProjectModelScopeIdentifier> getProducers() {
        return producers;
    }

    public boolean isLenient() {
        return kind == Kind.LENIENT;
    }

    public boolean isAllValues() {
        return kind == Kind.ALL_VALUES;
    }

    public boolean isAllModels() {
        return kind == Kind.ALL_MODELS;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof ProjectScopeModelBatchRequest)) {
            return false;
        }

        ProjectScopeModelBatchRequest<?> that = (ProjectScopeModelBatchRequest<?>) o;
        return kind == that.kind && consumer.equals(that.consumer) && key.equals(that.key) && producers.equals(that.producers);
    }

    @Override
    public int hashCode() {
        int result = consumer.hashCode();
        result = 31 * result + key.hashCode();
        result = 31 * result + producers.hashCode();
        return result;
    }

    public static <T> ProjectScopeModelBatchRequest<T> allValues(ProjectModelScopeIdentifier consumerScope, IsolatedModelKey<T> key) {
        return new ProjectScopeModelBatchRequest<>(consumerScope, key);
    }

    public static <T> ProjectScopeModelBatchRequest<T> allModels(ProjectModelScopeIdentifier consumerScope) {
        return new ProjectScopeModelBatchRequest<>(consumerScope);
    }
}
