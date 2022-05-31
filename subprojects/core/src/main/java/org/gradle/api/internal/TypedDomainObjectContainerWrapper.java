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
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.internal.metaobject.PropertyAccess;
import org.gradle.internal.metaobject.PropertyMixIn;
import org.gradle.util.internal.ConfigureUtil;

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

    @Override
    public U create(String name) throws InvalidUserDataException {
        return parent.create(name, type);
    }

    @Override
    public U create(String name, Action<? super U> configureAction) throws InvalidUserDataException {
        return parent.create(name, type, configureAction);
    }

    @Override
    public U create(String name, Closure configureClosure) throws InvalidUserDataException {
        return parent.create(name, type, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
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

    @Override
    public NamedDomainObjectContainer<U> configure(Closure configureClosure) {
        NamedDomainObjectContainerConfigureDelegate delegate = new NamedDomainObjectContainerConfigureDelegate(configureClosure, this);
        return ConfigureUtil.configureSelf(configureClosure, this, delegate);
    }

    @Override
    public NamedDomainObjectProvider<U> register(String name, Action<? super U> configurationAction) throws InvalidUserDataException {
        return parent.register(name, type, configurationAction);
    }

    @Override
    public NamedDomainObjectProvider<U> register(String name) throws InvalidUserDataException {
        return parent.register(name, type);
    }

    @Override
    public Set<U> findAll(Closure spec) {
        return delegate.findAll(spec);
    }

    @Override
    public NamedDomainObjectSet<U> matching(Closure spec) {
        return delegate.matching(spec);
    }

    @Override
    public NamedDomainObjectProvider<U> named(String name) throws UnknownDomainObjectException {
        return delegate.named(name);
    }

    @Override
    public NamedDomainObjectProvider<U> named(String name, Action<? super U> configurationAction) throws UnknownDomainObjectException {
        return delegate.named(name, configurationAction);
    }

    @Override
    public <S extends U> NamedDomainObjectProvider<S> named(String name, Class<S> type) throws UnknownDomainObjectException {
        return delegate.named(name, type);
    }

    @Override
    public <S extends U> NamedDomainObjectProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownDomainObjectException {
        return delegate.named(name, type, configurationAction);
    }

    @Override
    public NamedDomainObjectSet<U> matching(Spec<? super U> spec) {
        return delegate.matching(spec);
    }

    @Override
    public <S extends U> NamedDomainObjectSet<S> withType(Class<S> type) {
        return delegate.withType(type);
    }

    @Override
    public boolean add(U e) {
        return delegate.add(e);
    }

    @Override
    public void addLater(Provider<? extends U> provider) {
        delegate.addLater(provider);
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<U>> provider) {
        delegate.addAllLater(provider);
    }

    @Override
    public boolean addAll(Collection<? extends U> c) {
        return delegate.addAll(c);
    }

    @Override
    public Rule addRule(String description, Closure ruleAction) {
        return delegate.addRule(description, ruleAction);
    }

    @Override
    public Rule addRule(String description, Action<String> ruleAction) {
        return delegate.addRule(description, ruleAction);
    }

    @Override
    public Rule addRule(Rule rule) {
        return delegate.addRule(rule);
    }

    @Override
    public U findByName(String name) {
        return delegate.findByName(name);
    }

    @Override
    public SortedMap<String, U> getAsMap() {
        return delegate.getAsMap();
    }

    @Override
    public NamedDomainObjectCollectionSchema getCollectionSchema() {
        return delegate.getCollectionSchema();
    }

    @Override
    public U getAt(String name) throws UnknownDomainObjectException {
        return delegate.getAt(name);
    }

    @Override
    public U getByName(String name) throws UnknownDomainObjectException {
        return delegate.getByName(name);
    }

    @Override
    public U getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
        return delegate.getByName(name, configureClosure);
    }

    @Override
    public U getByName(String name, Action<? super U> configureAction) throws UnknownDomainObjectException {
        return delegate.getByName(name, configureAction);
    }

    @Override
    public Namer<U> getNamer() {
        return delegate.getNamer();
    }

    @Override
    public SortedSet<String> getNames() {
        return delegate.getNames();
    }

    @Override
    public List<Rule> getRules() {
        return delegate.getRules();
    }

    @Override
    public void all(Action<? super U> action) {
        delegate.all(action);
    }

    @Override
    public void all(Closure action) {
        delegate.all(action);
    }

    @Override
    public void configureEach(Action<? super U> action) {
        delegate.configureEach(action);
    }

    @Override
    public Action<? super U> whenObjectAdded(Action<? super U> action) {
        return delegate.whenObjectAdded(action);
    }

    @Override
    public void whenObjectAdded(Closure action) {
        delegate.whenObjectAdded(action);
    }

    @Override
    public Action<? super U> whenObjectRemoved(Action<? super U> action) {
        return delegate.whenObjectRemoved(action);
    }

    @Override
    public void whenObjectRemoved(Closure action) {
        delegate.whenObjectRemoved(action);
    }

    @Override
    public <S extends U> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        return delegate.withType(type, configureAction);
    }

    @Override
    public <S extends U> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return delegate.withType(type, configureClosure);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
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

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Iterator<U> iterator() {
        return delegate.iterator();
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return delegate.toArray(a);
    }
}
