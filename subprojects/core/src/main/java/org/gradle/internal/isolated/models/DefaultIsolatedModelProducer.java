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

import org.gradle.api.provider.Provider;

public class DefaultIsolatedModelProducer<T> implements IsolatedModelProducer<T> {

    private final Class<T> modelType;
    private final Provider<T> provider;

    public DefaultIsolatedModelProducer(Class<T> modelType, Provider<T> provider) {
        this.modelType = modelType;
        this.provider = provider;
    }

    @Override
    public Class<T> getModelType() {
        return modelType;
    }

    @Override
    public Provider<T> prepare() {
        // Mapping here to enforce task dependencies check,
        // so that task-dependency-carrying properties cannot be evaluated at configuration time
        // TODO:provider-api this should work out of the box for providers
        return provider.map(it -> it);
    }
}
