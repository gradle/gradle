/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.provider.PropertyState;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

public class DefaultPropertyState<T> implements PropertyState<T>, ProviderInternal<T> {
    private final Class<T> type;
    private Provider<? extends T> provider = Providers.notDefined();

    public DefaultPropertyState(Class<T> type) {
        this.type = type;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public void set(final T value) {
        if (value == null) {
            this.provider = Providers.notDefined();
            return;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s using an instance of type %s.", type.getName(), value.getClass().getName()));
        }
        this.provider = Providers.of(value);
    }

    protected Provider<? extends T> getProvider() {
        return provider;
    }

    @Override
    public void set(final Provider<? extends T> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Cannot set the value of a property using a null provider.");
        }
        ProviderInternal<T> p = Cast.uncheckedCast(provider);
        if (p.getType() != null && !type.isAssignableFrom(p.getType())) {
            throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s using a provider of type %s.", type.getName(), p.getType().getName()));
        } else if (p.getType() == null) {
            p = new AbstractProvider<T>() {
                @Nullable
                @Override
                public Class<T> getType() {
                    return null;
                }

                @Override
                public T getOrNull() {
                    T value = provider.getOrNull();
                    if (value == null || type.isInstance(value)) {
                        return value;
                    }
                    throw new IllegalArgumentException(String.format("Cannot get the value of a property of type %s as the provider associated with this property returned a value of type %s.", type.getName(), value.getClass().getName()));
                }
            };
        }

        this.provider = p;
    }

    @Override
    public T get() {
        return provider.get();
    }

    @Override
    public T getOrNull() {
        return provider.getOrNull();
    }

    @Override
    public boolean isPresent() {
        return provider.isPresent();
    }

    @Override
    public String toString() {
        return String.format("value: %s", getOrNull());
    }
}
