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

package org.gradle.api.internal.provider;

import org.gradle.internal.UncheckedException;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;

/**
 * A provider whose value is computed by a {@link Callable}. This provider is very similar to {@link DefaultProvider},
 * except it guarantees that the value is always present. This way, we can avoid eagerly calculating the value
 * when calling {@link #isPresent()}.
 *
 * <h3>Configuration Cache Behavior</h3>
 * <b>Eager</b>. The value is computed at store time and loaded from the cache.
 */
public class DefaultProviderWithValue<T> extends AbstractProviderWithValue<T> {
    private final Callable<? extends T> value;

    public DefaultProviderWithValue(Callable<? extends T> value) {
        this.value = value;
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try {
            T result = value.call();
            if (result == null) {
                throw new IllegalArgumentException("Callable must never return null.");
            }
            return Value.of(result);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Nullable
    @Override
    public Class<T> getType() {
        // guard against https://youtrack.jetbrains.com/issue/KT-36297
        try {
            return DefaultProvider.inferTypeFromCallableGenericArgument(value);
        } catch (NoClassDefFoundError e) {
            return null;
        }
    }
}
