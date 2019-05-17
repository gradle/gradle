/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

public class DefaultPolymorphicNamedEntityInstantiator<T> implements PolymorphicNamedEntityInstantiator<T> {
    private final Map<Class<? extends T>, NamedDomainObjectFactory<? extends T>> factories = Maps.newHashMap();
    private final Class<? extends T> baseType;
    private final String displayName;

    public DefaultPolymorphicNamedEntityInstantiator(Class<? extends T> type, String displayName) {
        this.displayName = displayName;
        this.baseType = type;
    }

    @Override
    public <S extends T> S create(String name, Class<S> type) {
        @SuppressWarnings("unchecked")
        NamedDomainObjectFactory<S> factory = (NamedDomainObjectFactory<S>) factories.get(type);
        if (factory == null) {
            throw new InvalidUserDataException(
                    String.format("Cannot create a %s because this type is not known to %s. Known types are: %s", type.getSimpleName(), displayName, getSupportedTypeNames()),
                    new NoFactoryRegisteredForTypeException());
        }
        return factory.create(name);
    }

    public String getSupportedTypeNames() {
        List<String> names = Lists.newArrayList();
        for (Class<?> clazz : factories.keySet()) {
            names.add(clazz.getSimpleName());
        }
        Collections.sort(names);
        return names.isEmpty() ? "(None)" : Joiner.on(", ").join(names);
    }

    @Override
    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
        if (!baseType.isAssignableFrom(type)) {
            String message = String.format("Cannot register a factory for type %s because it is not a subtype of container element type %s.", type.getSimpleName(), baseType.getSimpleName());
            throw new IllegalArgumentException(message);
        }
        if(factories.containsKey(type)){
            throw new GradleException(String.format("Cannot register a factory for type %s because a factory for this type is already registered.", type.getSimpleName()));
        }
        factories.put(type, factory);
    }

    @Override
    public Set<? extends Class<? extends T>> getCreatableTypes() {
        return ImmutableSet.copyOf(factories.keySet());
    }

    public void copyFactoriesFrom(DefaultPolymorphicNamedEntityInstantiator<T> source) {
        for (Class<? extends T> languageType : source.factories.keySet()) {
            copyFactory(source, languageType);
        }
    }

    <U extends T> void copyFactory(DefaultPolymorphicNamedEntityInstantiator<T> source, Class<U> type) {
        NamedDomainObjectFactory<U> factory = uncheckedCast(source.factories.get(type));
        registerFactory(type, factory);
    }
}
