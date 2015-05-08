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
import org.gradle.api.Namer;
import org.gradle.api.Nullable;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;

import java.util.Collection;

import static org.gradle.internal.Cast.uncheckedCast;

abstract public class DomainObjectCollectionBackedModelMap<T, C extends DomainObjectCollection<T>> implements ModelMap<T> {

    protected final C backingCollection;
    protected final Class<T> elementClass;
    protected final NamedEntityInstantiator<T> instantiator;
    protected final Namer<Object> namer;

    protected DomainObjectCollectionBackedModelMap(Class<T> elementClass, C backingCollection, NamedEntityInstantiator<T> instantiator, Namer<Object> namer) {
        this.elementClass = elementClass;
        this.backingCollection = backingCollection;
        this.instantiator = instantiator;
        this.namer = namer;
    }

    @Override
    public <S> ModelMap<S> withType(final Class<S> type) {
        if (type.equals(elementClass)) {
            return uncheckedCast(this);
        }

        if (elementClass.isAssignableFrom(type)) {
            Class<? extends T> castType = uncheckedCast(type);
            ModelMap<? extends T> subType = toSubtypeMap(castType);
            return uncheckedCast(subType);
        }

        return toNonSubtypeMap(type);
    }

    protected abstract <S> ModelMap<S> toNonSubtypeMap(Class<S> type);

    protected abstract <S extends T> ModelMap<S> toSubtypeMap(Class<S> castType);

    @Override
    public int size() {
        return backingCollection.size();
    }

    @Override
    public boolean isEmpty() {
        return backingCollection.isEmpty();
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
        return backingCollection.contains(item);
    }

    @Override
    public void create(String name) {
        create(name, elementClass, Actions.doNothing());
    }

    @Override
    public void create(String name, Action<? super T> configAction) {
        create(name, elementClass, configAction);
    }

    @Override
    public <S extends T> void create(String name, Class<S> type) {
        create(name, type, Actions.doNothing());
    }

    @Override
    public <S extends T> void create(String name, Class<S> type, Action<? super S> configAction) {
        instantiator.create(name, type);
        S task = Cast.uncheckedCast(get(name));
        configAction.execute(task);
        onCreate(task);
    }

    protected void onCreate(T item) {
    }

    @Override
    public void named(String name, Class<? extends RuleSource> ruleSource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void all(Action<? super T> configAction) {
        backingCollection.all(configAction);
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
        return backingCollection;
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
    public void named(String name, Action<? super T> configAction) {
        backingCollection.matching(new WithName<T>(name, namer)).all(configAction);
    }

    private static class WithName<T> implements Spec<T> {
        private final String name;
        private final org.gradle.api.Namer<? super T> namer;

        public WithName(String name, org.gradle.api.Namer<? super T> namer) {
            this.name = name;
            this.namer = namer;
        }

        @Override
        public boolean isSatisfiedBy(T element) {
            return namer.determineName(element).equals(name);
        }
    }
}
