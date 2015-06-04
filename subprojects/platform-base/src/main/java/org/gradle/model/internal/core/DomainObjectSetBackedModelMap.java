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
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Namer;
import org.gradle.api.Nullable;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.Specs;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;

import java.util.Collection;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

public class DomainObjectSetBackedModelMap<T> implements ModelMap<T> {

    private final Class<T> elementType;
    private final DomainObjectSet<T> set;
    private final NamedEntityInstantiator<T> instantiator;
    private final Namer<? super T> namer;
    private final Action<? super T> onCreateAction;

    public DomainObjectSetBackedModelMap(Class<T> elementType, DomainObjectSet<T> backingSet, NamedEntityInstantiator<T> instantiator, Namer<? super T> namer, Action<? super T> onCreateAction) {
        this.elementType = elementType;
        this.set = backingSet;
        this.instantiator = instantiator;
        this.namer = namer;
        this.onCreateAction = onCreateAction;
    }

    private <S> ModelMap<S> toNonSubtypeMap(Class<S> type) {
        DomainObjectSet<S> cast = toNonSubtype(type);
        Namer<S> castNamer = Cast.uncheckedCast(namer);
        return DomainObjectSetBackedModelMap.wrap(type, cast, NamedEntityInstantiators.nonSubtype(type, elementType), castNamer, Actions.doNothing());
    }

    private <S> DomainObjectSet<S> toNonSubtype(final Class<S> type) {
        return uncheckedCast(set.matching(Specs.isInstance(type)));
    }

    private <S extends T> ModelMap<S> toSubtypeMap(Class<S> itemSubtype) {
        NamedEntityInstantiator<S> instantiator = uncheckedCast(this.instantiator);
        return DomainObjectSetBackedModelMap.wrap(itemSubtype, set.withType(itemSubtype), instantiator, namer, onCreateAction);
    }

    @Nullable
    @Override
    public T get(String name) {
        return Iterables.find(set, new HasNamePredicate<T>(name, namer), null);
    }

    @Override
    public Set<String> keySet() {
        return Sets.newHashSet(Iterables.transform(set, new ToName<T>(namer)));
    }

    @Override
    public <S> void withType(Class<S> type, Action<? super S> configAction) {
        toNonSubtype(type).all(configAction);
    }

    private static class HasNamePredicate<T> implements Predicate<T> {
        private final String name;
        private final Namer<? super T> namer;

        public HasNamePredicate(String name, Namer<? super T> namer) {
            this.name = name;
            this.namer = namer;
        }

        @Override
        public boolean apply(@Nullable T input) {
            return namer.determineName(input).equals(name);
        }
    }

    private static class ToName<T> implements Function<T, String> {
        private final Namer<? super T> namer;

        public ToName(Namer<? super T> namer) {
            this.namer = namer;
        }

        @Override
        public String apply(@Nullable T input) {
            return namer.determineName(input);
        }
    }

    public static <T> DomainObjectSetBackedModelMap<T> wrap(Class<T> elementType, DomainObjectSet<T> domainObjectSet, NamedEntityInstantiator<T> instantiator, Namer<? super T> namer, Action<? super T> onCreate) {
        return new DomainObjectSetBackedModelMap<T>(elementType, domainObjectSet, instantiator, namer, onCreate);
    }

    @Override
    public int size() {
        return set.size();
    }

    @Override
    public boolean isEmpty() {
        return set.isEmpty();
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
        return set.contains(item);
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
        S s = instantiator.create(name, type);
        configAction.execute(s);
        onCreateAction.execute(s);
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void all(Action<? super T> configAction) {
        set.all(configAction);
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
        return set;
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
        set.matching(new Spec<T>() {
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
