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
import org.gradle.util.internal.ConfigureUtil;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class DelegatingDomainObjectSet<T> implements DomainObjectSet<T>, DomainObjectCollectionInternal<T> {
    private final DomainObjectSet<T> delegate;

    public DelegatingDomainObjectSet(DomainObjectSet<T> delegate) {
        this.delegate = delegate;
    }

    protected DomainObjectSet<T> getDelegate() {
        return delegate;
    }

    @Override
    public DomainObjectSet<T> matching(Closure spec) {
        return matching(Specs.convertClosureToSpec(spec));
    }

    @Override
    public DomainObjectSet<T> matching(Spec<? super T> spec) {
        return delegate.matching(spec);
    }

    @Override
    public <S extends T> DomainObjectSet<S> withType(Class<S> type) {
        return delegate.withType(type);
    }

    @Override
    public void all(Action<? super T> action) {
        delegate.all(action);
    }

    @Override
    public void all(Closure action) {
        all(ConfigureUtil.configureUsing(action));
    }

    @Override
    public void configureEach(Action<? super T> action) {
        delegate.configureEach(action);
    }

    @Override
    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return delegate.whenObjectAdded(action);
    }

    @Override
    public void whenObjectAdded(Closure action) {
        whenObjectAdded(ConfigureUtil.configureUsing(action));
    }

    @Override
    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return delegate.whenObjectRemoved(action);
    }

    @Override
    public void whenObjectRemoved(Closure action) {
        whenObjectRemoved(ConfigureUtil.configureUsing(action));
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        return delegate.withType(type, configureAction);
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return withType(type, ConfigureUtil.configureUsing(configureClosure));
    }

    @Override
    public void addLater(Provider<? extends T> provider) {
        delegate.addLater(provider);
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<T>> provider) {
        delegate.addAllLater(provider);
    }

    @Override
    public boolean add(T o) {
        return delegate.add(o);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return delegate.addAll(c);
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
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public Iterator<T> iterator() {
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

    @Override
    public Set<T> findAll(Closure spec) {
        return delegate.findAll(spec);
    }

    @Override
    public int estimatedSize() {
        return ((DomainObjectCollectionInternal<?>) delegate).estimatedSize();
    }

    @Override
    public void beforeCollectionChanges(Action<String> action) {
        ((DomainObjectCollectionInternal<?>) delegate).beforeCollectionChanges(action);
    }

    @Override
    public String getDisplayName() {
        return ((DomainObjectCollectionInternal<?>) delegate).getDisplayName();
    }
}
