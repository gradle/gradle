/*
 * Copyright 2011 the original author or authors.
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
import org.gradle.api.DomainObjectSet;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.ConfigureUtil;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class DelegatingDomainObjectSet<T> implements DomainObjectSet<T> {
    private final DomainObjectSet<T> backingSet;

    public DelegatingDomainObjectSet(DomainObjectSet<T> backingSet) {
        this.backingSet = backingSet;
    }

    @Override
    public DomainObjectSet<T> matching(Closure spec) {
        return matching(Specs.convertClosureToSpec(spec));
    }

    @Override
    public DomainObjectSet<T> matching(Spec<? super T> spec) {
        return backingSet.matching(spec);
    }

    @Override
    public <S extends T> DomainObjectSet<S> withType(Class<S> type) {
        return backingSet.withType(type);
    }

    @Override
    public void all(Action<? super T> action) {
        backingSet.all(action);
    }

    @Override
    public void all(Closure action) {
        all(ConfigureUtil.configureUsing(action));
    }

    @Override
    public void configureEach(Action<? super T> action) {
        backingSet.configureEach(action);
    }

    @Override
    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return backingSet.whenObjectAdded(action);
    }

    @Override
    public void whenObjectAdded(Closure action) {
        whenObjectAdded(ConfigureUtil.configureUsing(action));
    }

    @Override
    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return backingSet.whenObjectRemoved(action);
    }

    @Override
    public void whenObjectRemoved(Closure action) {
        whenObjectRemoved(ConfigureUtil.configureUsing(action));
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        return backingSet.withType(type, configureAction);
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return withType(type, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public void addLater(Provider<? extends T> provider) {
        backingSet.addLater(provider);
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<T>> provider) {
        backingSet.addAllLater(provider);
    }

    @Override
    public boolean add(T o) {
        return backingSet.add(o);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return backingSet.addAll(c);
    }

    @Override
    public void clear() {
        backingSet.clear();
    }

    @Override
    public boolean contains(Object o) {
        return backingSet.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return backingSet.containsAll(c);
    }

    @Override
    public boolean isEmpty() {
        return backingSet.isEmpty();
    }

    @Override
    public Iterator<T> iterator() {
        return backingSet.iterator();
    }

    @Override
    public boolean remove(Object o) {
        return backingSet.remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return backingSet.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return backingSet.retainAll(c);
    }

    @Override
    public int size() {
        return backingSet.size();
    }

    @Override
    public Object[] toArray() {
        return backingSet.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return backingSet.toArray(a);
    }

    @Override
    public Set<T> findAll(Closure spec) {
        return backingSet.findAll(spec);
    }
}
