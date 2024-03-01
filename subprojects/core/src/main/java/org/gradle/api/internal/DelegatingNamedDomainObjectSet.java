/*
 * Copyright 2023 the original author or authors.
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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.specs.Spec;

import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * A {@Link NamedDomainObjectSet} which delegates all methods to a provided delegate.
 */
public class DelegatingNamedDomainObjectSet<T> extends DelegatingDomainObjectSet<T> implements NamedDomainObjectSet<T> {

    public DelegatingNamedDomainObjectSet(NamedDomainObjectSet<T> backingSet) {
        super(backingSet);
    }

    @Override
    protected NamedDomainObjectSet<T> getDelegate() {
        return (NamedDomainObjectSet<T>) super.getDelegate();
    }

    @Override
    public <S extends T> NamedDomainObjectSet<S> withType(Class<S> type) {
        return getDelegate().withType(type);
    }

    @Override
    public NamedDomainObjectSet<T> named(Spec<String> nameFilter) {
        return getDelegate().named(nameFilter);
    }

    @Override
    public NamedDomainObjectSet<T> matching(Spec<? super T> spec) {
        return getDelegate().matching(spec);
    }

    @Override
    public NamedDomainObjectSet<T> matching(Closure spec) {
        return getDelegate().matching(spec);
    }

    @Override
    public NamedDomainObjectProvider<T> named(String name) throws UnknownDomainObjectException {
        return getDelegate().named(name);
    }

    @Override
    public NamedDomainObjectProvider<T> named(String name, Action<? super T> configurationAction) throws UnknownDomainObjectException {
        return getDelegate().named(name, configurationAction);
    }

    @Override
    public <S extends T> NamedDomainObjectProvider<S> named(String name, Class<S> type) throws UnknownDomainObjectException {
        return getDelegate().named(name, type);
    }

    @Override
    public <S extends T> NamedDomainObjectProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownDomainObjectException {
        return getDelegate().named(name, type, configurationAction);
    }

    @Override
    public Rule addRule(String description, Closure ruleAction) {
        return getDelegate().addRule(description, ruleAction);
    }

    @Override
    public Rule addRule(String description, Action<String> ruleAction) {
        return getDelegate().addRule(description, ruleAction);
    }

    @Override
    public Rule addRule(Rule rule) {
        return getDelegate().addRule(rule);
    }

    @Override
    public T findByName(String name) {
        return getDelegate().findByName(name);
    }

    @Override
    public SortedMap<String, T> getAsMap() {
        return getDelegate().getAsMap();
    }

    @Override
    public NamedDomainObjectCollectionSchema getCollectionSchema() {
        return getDelegate().getCollectionSchema();
    }

    @Override
    public T getAt(String name) throws UnknownDomainObjectException {
        return getDelegate().getAt(name);
    }

    @Override
    public T getByName(String name) throws UnknownDomainObjectException {
        return getDelegate().getByName(name);
    }

    @Override
    public T getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
        return getDelegate().getByName(name, configureClosure);
    }

    @Override
    public T getByName(String name, Action<? super T> configureAction) throws UnknownDomainObjectException {
        return getDelegate().getByName(name, configureAction);
    }

    @Override
    public Namer<T> getNamer() {
        return getDelegate().getNamer();
    }

    @Override
    public SortedSet<String> getNames() {
        return getDelegate().getNames();
    }

    @Override
    public List<Rule> getRules() {
        return getDelegate().getRules();
    }

}
