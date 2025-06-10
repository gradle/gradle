/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.jspecify.annotations.Nullable;

/**
 * Enforces that a given delegate provider always provides a present value, throwing an exception if
 * the value is not present.
 * <p>
 * To be used when a provider should always be present, for example when an API documents that
 * a provider must always be present. In which case, this provider is able to avoid calculating
 * the provider's value when calling {@link #calculatePresence(ValueConsumer)}, and therefore
 * avoid eagerly realizing that value before it is actually needed.
 */
public class DelegatingProviderWithValue<T> extends AbstractProviderWithValue<T> {

    private final ProviderInternal<T> delegate;
    private final String nonPresentMessage;

    public DelegatingProviderWithValue(ProviderInternal<T> delegate, String nonPresentMessage) {
        this.delegate = delegate;
        this.nonPresentMessage = nonPresentMessage;
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            Value<? extends T> value = delegate.calculateValue(consumer);
            if (value.isMissing()) {
                throw new IllegalStateException(nonPresentMessage);
            }
            return value;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return delegate.getType();
    }

}
