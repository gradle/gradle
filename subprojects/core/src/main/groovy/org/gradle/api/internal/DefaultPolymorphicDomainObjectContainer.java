/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.*;
import org.gradle.internal.reflect.Instantiator;

import java.util.HashMap;
import java.util.Map;

public class DefaultPolymorphicDomainObjectContainer<T> extends AbstractPolymorphicDomainObjectContainer<T> {
    private final Map<Class<?>, NamedDomainObjectFactory<?>> factories =
            new HashMap<Class<?>, NamedDomainObjectFactory<?>>();

    private NamedDomainObjectFactory<? extends T> defaultFactory;

    public DefaultPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator, Namer<? super T> namer) {
        super(type, instantiator, namer);
    }

    public DefaultPolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator) {
        this(type, instantiator, Named.Namer.forType(type));
    }

    protected T doCreate(String name) {
        return defaultFactory.create(name);
    }

    protected  <U extends T> U doCreate(String name, Class<U> type) {
        return getFactory(type).create(name);
    }

    public void registerDefaultFactory(NamedDomainObjectFactory<? extends T> factory) {
        defaultFactory = factory;
    }

    public <U extends T> void registerFactory(Class<U> type, NamedDomainObjectFactory<? extends U> factory) {
        factories.put(type, factory);
    }

    @SuppressWarnings("unchecked")
    private <U extends T> NamedDomainObjectFactory<U> getFactory(Class<U> type) {
        return (NamedDomainObjectFactory<U>) factories.get(type);
    }
}
