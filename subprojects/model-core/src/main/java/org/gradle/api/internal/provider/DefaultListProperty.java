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

import com.google.common.collect.ImmutableList;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;

public class DefaultListProperty<T> extends AbstractCollectionProperty<T, List<T>> implements ListProperty<T> {
    public DefaultListProperty(Class<T> elementType) {
        super(List.class, elementType);
    }

    @Override
    public Class<?> publicType() {
        return ListProperty.class;
    }

    @Override
    public Factory managedFactory() {
        return new Factory() {
            @Nullable
            @Override
            public <S> S fromState(Class<S> type, Object state) {
                if (!type.isAssignableFrom(ListProperty.class)) {
                    return null;
                }
                DefaultListProperty<T> property = new DefaultListProperty<>(DefaultListProperty.this.getElementType());
                property.set((List<T>) state);
                return type.cast(property);
            }
        };
    }

    @Override
    protected List<T> fromValue(Collection<T> values) {
        return ImmutableList.copyOf(values);
    }

    @Override
    public ListProperty<T> empty() {
        super.empty();
        return this;
    }

    @Override
    public ListProperty<T> convention(Iterable<? extends T> elements) {
        super.convention(elements);
        return this;
    }

    @Override
    public ListProperty<T> convention(Provider<? extends Iterable<? extends T>> provider) {
        super.convention(provider);
        return this;
    }
}
