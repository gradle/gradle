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

import java.util.List;

public class ProjectScopeModelBatchRequest<T> {

    private final ProjectModelScopeIdentifier consumer;
    private final IsolatedModelKey<T> key;
    private final List<ProjectModelScopeIdentifier> producers;
    private final boolean lenient;

    public ProjectScopeModelBatchRequest(ProjectModelScopeIdentifier consumer, IsolatedModelKey<T> key, List<ProjectModelScopeIdentifier> producers, boolean lenient) {
        this.consumer = consumer;
        this.key = key;
        this.producers = producers;
        this.lenient = lenient;
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
        return lenient;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof ProjectScopeModelBatchRequest)) {
            return false;
        }

        ProjectScopeModelBatchRequest<?> that = (ProjectScopeModelBatchRequest<?>) o;
        return consumer.equals(that.consumer) && key.equals(that.key) && producers.equals(that.producers);
    }

    @Override
    public int hashCode() {
        int result = consumer.hashCode();
        result = 31 * result + key.hashCode();
        result = 31 * result + producers.hashCode();
        return result;
    }
}
