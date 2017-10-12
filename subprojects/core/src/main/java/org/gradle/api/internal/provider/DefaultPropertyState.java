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

import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

public class DefaultPropertyState<T> implements PropertyInternal<T> {
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
    public void setFromAnyValue(Object object) {
        if (object instanceof Provider) {
            set((Provider<T>) object);
        } else {
            set((T) object);
        }
    }

    @Override
    public void set(T value) {
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
    public void set(Provider<? extends T> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Cannot set the value of a property using a null provider.");
        }
        ProviderInternal<T> p = Cast.uncheckedCast(provider);
        if (p.getType() != null && !type.isAssignableFrom(p.getType())) {
            throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s using a provider of type %s.", type.getName(), p.getType().getName()));
        } else if (p.getType() == null) {
            p = p.map(new Transformer<T, T>() {
                @Override
                public T transform(T t) {
                    if (type.isInstance(t)) {
                        return t;
                    }
                    throw new IllegalArgumentException(String.format("Cannot get the value of a property of type %s as the provider associated with this property returned a value of type %s.", type.getName(), t.getClass().getName()));
                }
            });
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
    public T getOrElse(T defaultValue) {
        T t = provider.getOrNull();
        if (t == null) {
            return defaultValue;
        }
        return t;
    }

    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super T> transformer) {
        return new TransformBackedProvider<S, T>(transformer, this);
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
