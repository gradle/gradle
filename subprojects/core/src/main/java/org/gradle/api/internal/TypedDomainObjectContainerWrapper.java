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
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.specs.Spec;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.internal.metaobject.PropertyAccess;
import org.gradle.internal.metaobject.PropertyMixIn;
import org.gradle.util.ConfigureUtil;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

public class TypedDomainObjectContainerWrapper<U> implements NamedDomainObjectContainer<U>, MethodMixIn, PropertyMixIn {
    private final Class<U> type;
    private final AbstractPolymorphicDomainObjectContainer<? super U> parent;
    private final NamedDomainObjectSet<U> delegate;

    public TypedDomainObjectContainerWrapper(Class<U> type, AbstractPolymorphicDomainObjectContainer<? super U> parent) {
        this.parent = parent;
        this.type = type;
        this.delegate = parent.withType(type);
    }

    public U create(String name) throws InvalidUserDataException {
        return parent.create(name, type);
    }

    public U create(String name, Action<? super U> configureAction) throws InvalidUserDataException {
        return parent.create(name, type, configureAction);
    }

    public U create(String name, Closure configureClosure) throws InvalidUserDataException {
        return parent.create(name, type, ConfigureUtil.configureUsing(configureClosure));
    }

    public U maybeCreate(String name) {
        return parent.maybeCreate(name, type);
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return parent.getAdditionalMethods();
    }

    @Override
    public PropertyAccess getAdditionalProperties() {
        return parent.getAdditionalProperties();
    }

    public NamedDomainObjectContainer<U> configure(Closure configureClosure) {
        NamedDomainObjectContainerConfigureDelegate delegate = new NamedDomainObjectContainerConfigureDelegate(configureClosure, this);
        return ConfigureUtil.configureSelf(configureClosure, this, delegate);
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

    @Override
    public Rule addRule(String description, Action<String> ruleAction) {
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

    @Override
    public U getByName(String name, Action<? super U> configureAction) throws UnknownDomainObjectException {
        return delegate.getByName(name, configureAction);
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
