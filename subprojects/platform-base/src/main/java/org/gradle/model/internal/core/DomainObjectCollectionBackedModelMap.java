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
import org.gradle.api.Nullable;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;

import java.util.Collection;

import static org.gradle.internal.Cast.uncheckedCast;

abstract public class DomainObjectCollectionBackedModelMap<T> implements ModelMap<T> {

    protected final Class<T> elementClass;
    protected final NamedEntityInstantiator<T> instantiator;
    protected final Action<? super T> onCreateAction;

    protected DomainObjectCollectionBackedModelMap(Class<T> elementClass, NamedEntityInstantiator<T> instantiator, Action<? super T> onCreateAction) {
        this.elementClass = elementClass;
        this.instantiator = instantiator;
        this.onCreateAction = onCreateAction;
    }

    protected abstract DomainObjectCollection<T> getBackingCollection();

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
        return getBackingCollection().size();
    }

    @Override
    public boolean isEmpty() {
        return getBackingCollection().isEmpty();
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
        return getBackingCollection().contains(item);
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
        getBackingCollection().all(configAction);
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
        return getBackingCollection();
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
        getBackingCollection().matching(new Spec<T>() {
            @Override
            public boolean isSatisfiedBy(T element) {
                return get(name) == element;
            }
        }).all(configAction);
    }

}
