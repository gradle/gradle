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

package org.gradle.model.internal.core;

import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Nullable;
import org.gradle.api.internal.ExtensiblePolymorphicDomainObjectContainerInternal;
import org.gradle.internal.Actions;
import org.gradle.internal.Specs;
import org.gradle.model.ModelMap;

import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

public class NamedDomainObjectSetBackedModelMap<T> extends DomainObjectCollectionBackedModelMap<T> {

    private final NamedDomainObjectSet<T> backingCollection;

    private NamedDomainObjectSetBackedModelMap(Class<T> elementClass, NamedDomainObjectSet<T> backingCollection, NamedEntityInstantiator<T> instantiator, Action<? super T> onCreate) {
        super(elementClass, instantiator, onCreate);
        this.backingCollection = backingCollection;
    }

    @Override
    protected DomainObjectCollection<T> getBackingCollection() {
        return backingCollection;
    }

    private <S> NamedDomainObjectSet<S> toNonSubtype(final Class<S> type) {
        return uncheckedCast(backingCollection.matching(Specs.isInstance(type)));
    }

    @Override
    protected <S> ModelMap<S> toNonSubtypeMap(Class<S> type) {
        NamedDomainObjectSet<S> cast = toNonSubtype(type);
        return new NamedDomainObjectSetBackedModelMap<S>(type, cast, NamedEntityInstantiators.nonSubtype(type, elementClass), Actions.doNothing());
    }

    protected <S extends T> ModelMap<S> toSubtypeMap(Class<S> itemSubtype) {
        NamedEntityInstantiator<S> instantiator = uncheckedCast(this.instantiator);
        return new NamedDomainObjectSetBackedModelMap<S>(itemSubtype, backingCollection.withType(itemSubtype), instantiator, onCreateAction);
    }

    @Nullable
    @Override
    public T get(String name) {
        return backingCollection.findByName(name);
    }

    @Override
    public Set<String> keySet() {
        return backingCollection.getNames();
    }

    @Override
    public <S> void withType(Class<S> type, Action<? super S> configAction) {
        toNonSubtype(type).all(configAction);
    }

    public static <T> NamedDomainObjectSetBackedModelMap<T> wrap(Class<T> elementType, NamedDomainObjectSet<T> domainObjectSet, NamedEntityInstantiator<T> instantiator, Action<? super T> onCreate) {
        return new NamedDomainObjectSetBackedModelMap<T>(elementType, domainObjectSet, instantiator, onCreate);
    }

    public static <T> NamedDomainObjectSetBackedModelMap<T> wrap(Class<T> elementType, ExtensiblePolymorphicDomainObjectContainerInternal<T> container) {
        return wrap(elementType, container, container.getEntityInstantiator(), Actions.add(container));
    }

}
