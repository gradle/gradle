/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.*;
import org.gradle.api.internal.plugins.DefaultConvention;
import org.gradle.api.plugins.Convention;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import java.util.*;

public class TypedDomainObjectContainerWrapper<U> implements NamedDomainObjectContainer<U>, DynamicObjectAware {
    private final Class<U> type;
    private final PolymorphicDomainObjectContainer<? super U> parent;
    private final NamedDomainObjectSet<U> delegate;
    private final Convention convention;

    public TypedDomainObjectContainerWrapper(Class<U> type, PolymorphicDomainObjectContainer<? super U> parent, Instantiator instantiator) {
        this.parent = parent;
        this.type = type;
        this.delegate = parent.withType(type);
        this.convention = new DefaultConvention(instantiator);
    }

    public Convention getConvention() {
        return convention;
    }

    public U create(String name) throws InvalidUserDataException {
        return parent.create(name, type);
    }

    public U create(String name, Action<? super U> configureAction) throws InvalidUserDataException {
        return parent.create(name, type, configureAction);
    }

    public U create(String name, Closure configureClosure) throws InvalidUserDataException {
        return parent.create(name, type, new ClosureBackedAction<U>(configureClosure));
    }

    public U maybeCreate(String name) {
        return parent.maybeCreate(name, type);
    }

    public DynamicObject getAsDynamicObject() {
        return ((DynamicObjectAware) delegate).getAsDynamicObject();
    }

    public NamedDomainObjectContainer<U> configure(Closure configureClosure) {
        NamedDomainObjectContainerConfigureDelegate delegate = new NamedDomainObjectContainerConfigureDelegate(configureClosure.getOwner(), this);
        ConfigureUtil.configure(configureClosure, delegate);
        return this;
    }

    public Set<U> findAll(Closure spec) {
        return delegate.findAll(spec);
    }

    public NamedDomainObjectSet<U> matching(Closure spec) {
        return delegate.matching(spec);
    }

    public NamedDomainObjectSet<U> matching(Spec<? super U> spec) {
        return delegate.matching(spec);
    }

    public <S extends U> NamedDomainObjectSet<S> withType(Class<S> type) {
        return delegate.withType(type);
    }

    public boolean add(U e) {
        return delegate.add(e);
    }

    public boolean addAll(Collection<? extends U> c) {
        return delegate.addAll(c);
    }

    public Rule addRule(String description, Closure ruleAction) {
        return delegate.addRule(description, ruleAction);
    }

    public Rule addRule(Rule rule) {
        return delegate.addRule(rule);
    }

    public U findByName(String name) {
        return delegate.findByName(name);
    }

    public SortedMap<String, U> getAsMap() {
        return delegate.getAsMap();
    }

    public U getAt(String name) throws UnknownDomainObjectException {
        return delegate.getAt(name);
    }

    public U getByName(String name) throws UnknownDomainObjectException {
        return delegate.getByName(name);
    }

    public U getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
        return delegate.getByName(name, configureClosure);
    }

    public Namer<U> getNamer() {
        return delegate.getNamer();
    }

    public SortedSet<String> getNames() {
        return delegate.getNames();
    }

    public List<Rule> getRules() {
        return delegate.getRules();
    }

    public void all(Action<? super U> action) {
        delegate.all(action);
    }

    public void all(Closure action) {
        delegate.all(action);
    }

    public Action<? super U> whenObjectAdded(Action<? super U> action) {
        return delegate.whenObjectAdded(action);
    }

    public void whenObjectAdded(Closure action) {
        delegate.whenObjectAdded(action);
    }

    public Action<? super U> whenObjectRemoved(Action<? super U> action) {
        return delegate.whenObjectRemoved(action);
    }

    public void whenObjectRemoved(Closure action) {
        delegate.whenObjectRemoved(action);
    }

    public <S extends U> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        return delegate.withType(type, configureAction);
    }

    public <S extends U> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return delegate.withType(type, configureClosure);
    }

    public void clear() {
        delegate.clear();
    }

    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public Iterator<U> iterator() {
        return delegate.iterator();
    }

    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    public int size() {
        return delegate.size();
    }

    public Object[] toArray() {
        return delegate.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }
}
