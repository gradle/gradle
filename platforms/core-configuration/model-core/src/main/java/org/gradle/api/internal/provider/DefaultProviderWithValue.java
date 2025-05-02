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

import java.util.function.Supplier;

/**
 * Avoids executing the supplier (which may have side effects) when determining whether the
 * provider is present. The value supplier that backs this provider must always return
 * a non-null value.
 */
public class DefaultProviderWithValue<T> extends AbstractProviderWithValue<T> {

    private final Class<T> type;
    private final Supplier<T> supplier;

    public DefaultProviderWithValue(Class<T> type, Supplier<T> supplier) {
        this.type = type;
        this.supplier = supplier;
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext ignored = openScope()) {
            T value = supplier.get();
            if (value == null) {
                // AbstractProviderWithValue expects the factory to always return a non-null value
                throw new NullPointerException("Value factory must not return null");
            }
            return Value.of(value);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return type;
    }

}
