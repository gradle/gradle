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

package org.gradle.internal.isolated.models.legacy;

import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.gradle.internal.isolated.models.DefaultIsolatedModelKey;
import org.gradle.internal.isolated.models.IsolatedModelScope;

public class IsolatedModelProvider<T> extends AbstractMinimalProvider<T> {

    private final IsolatedModelScope producer;
    private final DefaultIsolatedModelKey<T> key;
    private final IsolatedModelScope consumer;

    private final IsolatedModelStore store;

    public IsolatedModelProvider(
        IsolatedModelScope producer,
        DefaultIsolatedModelKey<T> key,
        IsolatedModelScope consumer,
        IsolatedModelStore store
    ) {
        this.producer = producer;
        this.key = key;
        this.consumer = consumer;
        this.store = store;
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return getUnderlyingProvider().calculatePresence(consumer);
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            return getUnderlyingProvider().calculateValue(consumer);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public Class<T> getType() {
        return getUnderlyingProvider().getType();
    }

    private ProviderInternal<T> getUnderlyingProvider() {
        return store.getModel(consumer, key, producer);
    }
}
