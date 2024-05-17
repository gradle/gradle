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
import org.gradle.api.internal.lambdas.SerializableLambdas.SerializableSupplier;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedNonnullCast;

public class DefaultSetProperty<T> extends AbstractCollectionProperty<T, Set<T>> implements SetProperty<T> {
    private static final SerializableSupplier<ImmutableCollection.Builder<Object>> FACTORY = ImmutableSet::builder;

    /**
     * Convenience method to add a possibly-missing provider to a set property without clearing the set.
     *
     * @param set the set property to add to
     * @param provider the provider to add
     * @param <T> the type of the set elements
     */
    // Should be removed when https://github.com/gradle/gradle/issues/20266 is resolved
    public static <T> void addOptionalProvider(SetProperty<T> set, Provider<? extends T> provider) {
        set.addAll(provider.map(Collections::singleton).orElse(Collections.emptySet()));
    }

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
    public SetProperty<T> unset() {
        return uncheckedNonnullCast(super.unset());
    }

    @Override
    public SetProperty<T> unsetConvention() {
        return uncheckedNonnullCast(super.unsetConvention());
    }
}
