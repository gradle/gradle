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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Specs;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

public class DomainObjectCollectionBackedModelMap<T> extends ModelMapGroovyView<T> {

    private final String name;
    private final Class<T> elementType;
    private final DomainObjectCollection<T> collection;
    private final NamedEntityInstantiator<T> instantiator;
    private final org.gradle.api.Namer<? super T> namer;
    private final Action<? super T> onCreateAction;

    public DomainObjectCollectionBackedModelMap(String name, Class<T> elementType, DomainObjectCollection<T> backingCollection, NamedEntityInstantiator<T> instantiator, org.gradle.api.Namer<? super T> namer, Action<? super T> onCreateAction) {
        this.name = name;
        this.elementType = elementType;
        this.collection = backingCollection;
        this.instantiator = instantiator;
        this.namer = namer;
        this.onCreateAction = onCreateAction;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return collection.toString();
    }

    private <S> ModelMap<S> toNonSubtypeMap(Class<S> type) {
        DomainObjectCollection<S> cast = toNonSubtype(type);
        org.gradle.api.Namer<S> castNamer = Cast.uncheckedCast(namer);
        return DomainObjectCollectionBackedModelMap.wrap(name, type, cast, NamedEntityInstantiators.nonSubtype(type, elementType), castNamer, Actions.doNothing());
    }

    private <S> DomainObjectSet<S> toNonSubtype(final Class<S> type) {
        return uncheckedCast(collection.matching(Specs.isInstance(type)));
    }

    private <S extends T> ModelMap<S> toSubtypeMap(Class<S> itemSubtype) {
        NamedEntityInstantiator<S> instantiator = uncheckedCast(this.instantiator);
        return DomainObjectCollectionBackedModelMap.wrap(name, itemSubtype, collection.withType(itemSubtype), instantiator, namer, onCreateAction);
    }

    @Nullable
    @Override
    public T get(String name) {
        return Iterables.find(collection, new HasNamePredicate<T>(name, namer), null);
    }

    @Override
    public Set<String> keySet() {
        return Sets.newHashSet(Iterables.transform(collection, new ToName<T>(namer)));
    }

    @Override
    public <S> void withType(Class<S> type, Action<? super S> configAction) {
        toNonSubtype(type).all(configAction);
    }

    private static class HasNamePredicate<T> implements Predicate<T> {
        private final String name;
        private final org.gradle.api.Namer<? super T> namer;

        public HasNamePredicate(String name, org.gradle.api.Namer<? super T> namer) {
            this.name = name;
            this.namer = namer;
        }

        @Override
        public boolean apply(@Nullable T input) {
            return namer.determineName(input).equals(name);
        }
    }

    private static class ToName<T> implements Function<T, String> {
        private final org.gradle.api.Namer<? super T> namer;

        public ToName(org.gradle.api.Namer<? super T> namer) {
            this.namer = namer;
        }

        @Override
        public String apply(@Nullable T input) {
            return namer.determineName(input);
        }
    }

    public static <T> DomainObjectCollectionBackedModelMap<T> wrap(String name, Class<T> elementType, DomainObjectCollection<T> domainObjectSet, NamedEntityInstantiator<T> instantiator, org.gradle.api.Namer<? super T> namer, Action<? super T> onCreate) {
        return new DomainObjectCollectionBackedModelMap<T>(name, elementType, domainObjectSet, instantiator, namer, onCreate);
    }

    @Override
    public int size() {
        return collection.size();
    }

    @Override
    public boolean isEmpty() {
        return collection.isEmpty();
    }

    @Nullable
    @Override
    public T get(Object name) {
        return get(name.toString());
    }

    @Override
    public boolean containsKey(Object name) {
        return keySet().contains(name.toString());
    }

    @Override
    public boolean containsValue(Object item) {
        //noinspection SuspiciousMethodCalls
        return collection.contains(item);
    }

    @Override
    public void create(String name) {
        create(name, elementType, Actions.doNothing());
    }

    @Override
    public void create(String name, Action<? super T> configAction) {
        create(name, elementType, configAction);
    }

    @Override
    public <S extends T> void create(String name, Class<S> type) {
        create(name, type, Actions.doNothing());
    }

    @Override
    public <S extends T> void create(String name, Class<S> type, Action<? super S> configAction) {
        if (containsKey(name)) {
            throw new IllegalStateException("Entry with name already exists: " + name);
        }
        S s = instantiator.create(name, type);
        configAction.execute(s);
        onCreateAction.execute(s);
        collection.add(s);
    }

    @Override
    public void put(String name, T instance) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void all(Action<? super T> configAction) {
        collection.all(configAction);
    }

    @Override
    public void beforeEach(Action<? super T> configAction) {
        all(configAction);
    }

    @Override
    public <S> void withType(Class<S> type, Class<? extends RuleSource> rules) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void afterEach(Action<? super T> configAction) {
        all(configAction);
    }

    @Override
    public Collection<T> values() {
        return collection;
    }

    @Override
    public Iterator<T> iterator() {
        return collection.iterator();
    }

    @Override
    public <S> void beforeEach(Class<S> type, Action<? super S> configAction) {
        withType(type, configAction);
    }

    @Override
    public <S> void afterEach(Class<S> type, Action<? super S> configAction) {
        withType(type, configAction);
    }

    @Override
    public void named(final String name, Action<? super T> configAction) {
        collection.matching(new Spec<T>() {
            @Override
            public boolean isSatisfiedBy(T element) {
                return get(name) == element;
            }
        }).all(configAction);
    }

    @Override
    public <S> ModelMap<S> withType(final Class<S> type) {
        if (type.equals(elementType)) {
            return uncheckedCast(this);
        }

        if (elementType.isAssignableFrom(type)) {
            Class<? extends T> castType = uncheckedCast(type);
            ModelMap<? extends T> subType = toSubtypeMap(castType);
            return uncheckedCast(subType);
        }

        return toNonSubtypeMap(type);
    }

}
