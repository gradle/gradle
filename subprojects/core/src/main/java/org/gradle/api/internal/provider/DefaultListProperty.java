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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.List;

public class DefaultListProperty<T> implements PropertyInternal<List<T>>, ListProperty<T> {
    private static final Provider<ImmutableList<Object>> EMPTY_LIST = Providers.of(ImmutableList.of());
    private Provider<? extends List<T>> provider = Cast.uncheckedCast(EMPTY_LIST);

    public DefaultListProperty(Class<T> elementType) {
    }

    @Override
    public void add(final T element) {
        addAll(Providers.of(ImmutableList.of(
            Preconditions.checkNotNull(element, "Cannot add a null value to a list property."))));
    }

    @Override
    public void add(final Provider<? extends T> providerOfElement) {
        addAll(providerOfElement.map(new Transformer<Iterable<T>, T>() {
            @Override
            public Iterable<T> transform(T t) {
                return ImmutableList.of(t);
            }
        }));
    }

    @Override
    public void addAll(final Provider<? extends Iterable<T>> providerOfElements) {
        provider = provider.map(new Transformer<List<T>, List<T>>() {
            @Override
            public List<T> transform(List<T> ts) {
                return ImmutableList.<T>builder()
                    .addAll(ts)
                    .addAll(providerOfElements.get())
                    .build();
            }
        });
    }

    @Nullable
    @Override
    public Class<List<T>> getType() {
        return null;
    }

    @Override
    public boolean isPresent() {
        return provider.isPresent();
    }

    @Override
    public List<T> get() {
        return ImmutableList.copyOf(provider.get());
    }

    @Nullable
    @Override
    public List<T> getOrNull() {
        return getOrElse(null);
    }

    @Override
    public List<T> getOrElse(List<T> defaultValue) {
        List<T> list = provider.getOrNull();
        if (list == null) {
            return defaultValue;
        }
        return ImmutableList.copyOf(list);
    }

    @Override
    public void setFromAnyValue(Object object) {
        if (object instanceof Provider) {
            set((Provider<List<T>>) object);
        } else {
            if (object != null && !(object instanceof List)) {
                throw new IllegalArgumentException(String.format("Cannot set the value of a property of type %s using an instance of type %s.", List.class.getName(), object.getClass().getName()));
            }
            set((List<T>) object);
        }
    }

    @Override
    public void set(@Nullable List<T> value) {
        if (value == null) {
            this.provider = Providers.notDefined();
            return;
        }
        this.provider = Providers.of(value);
    }

    @Override
    public void set(Provider<? extends List<T>> provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Cannot set the value of a property using a null provider.");
        }
        this.provider = provider;
    }

    @Override
    public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super List<T>> transformer) {
        return new TransformBackedProvider<S, List<T>>(transformer, this) {
            @Override
            protected S map(List<T> v) {
                S result = super.map(v);
                if (result instanceof List) {
                    return (S) ImmutableList.copyOf((List<T>) result);
                }
                return result;
            }
        };
    }
}
