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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Supplier;

public class DefaultSetProperty<T> extends AbstractCollectionProperty<T, Set<T>> implements SetProperty<T> {
    private static final Supplier<ImmutableCollection.Builder<Object>> FACTORY = new Supplier<ImmutableCollection.Builder<Object>>() {
        @Override
        public ImmutableCollection.Builder<Object> get() {
            return ImmutableSet.builder();
        }
    };
    public DefaultSetProperty(PropertyHost host, Class<T> elementType) {
        super(host, Set.class, elementType, Cast.uncheckedNonnullCast(FACTORY));
    }

    @Override
    protected Set<T> emptyCollection() {
        return ImmutableSet.of();
    }

    @Override
    public Class<?> publicType() {
        return SetProperty.class;
    }

    @Override
    public int getFactoryId() {
        return ManagedFactories.SetPropertyManagedFactory.FACTORY_ID;
    }

    @Override
    public SetProperty<T> empty() {
        super.empty();
        return this;
    }

    @Override
    public SetProperty<T> value(@Nullable Iterable<? extends T> elements) {
        super.value(elements);
        return this;
    }

    @Override
    public SetProperty<T> value(Provider<? extends Iterable<? extends T>> provider) {
        super.value(provider);
        return this;
    }

    @Override
    public SetProperty<T> convention(@Nullable Iterable<? extends T> elements) {
        super.convention(elements);
        return this;
    }

    @Override
    public SetProperty<T> convention(Provider<? extends Iterable<? extends T>> provider) {
        super.convention(provider);
        return this;
    }

    @Override
    public SetProperty<T> updateSet(Transformer<? extends Provider<? extends Iterable<? extends T>>, ? super Provider<? extends Set<? extends T>>> transformer) {
        set(transformer.transform(freeze()));
        return this;
    }
}
