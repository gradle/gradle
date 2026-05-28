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
import org.gradle.api.tasks.Internal;

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

    protected void onMethodCall(String signature) {
        // Do nothing by default
    }

    @Override
    public DomainObjectSet<T> matching(Closure spec) {
        onMethodCall("matching(Closure)");
        return matching(Specs.convertClosureToSpec(spec));
    }

    @Override
    public DomainObjectSet<T> matching(Spec<? super T> spec) {
        onMethodCall("matching(Spec)");
        return getDelegate().matching(spec);
    }

    @Override
    public <S extends T> DomainObjectSet<S> withType(Class<S> type) {
        onMethodCall("withType(Class)");
        return getDelegate().withType(type);
    }

    @Override
    public void all(Action<? super T> action) {
        onMethodCall("all(Action)");
        getDelegate().all(action);
    }

    @Override
    public void all(Closure action) {
        onMethodCall("all(Closure)");
        getDelegate().all(action);
    }

    @Override
    public void configureEach(Action<? super T> action) {
        onMethodCall("configureEach(Action)");
        getDelegate().configureEach(action);
    }

    @Override
    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        onMethodCall("whenObjectAdded(Action)");
        return getDelegate().whenObjectAdded(action);
    }

    @Override
    public void whenObjectAdded(Closure action) {
        onMethodCall("whenObjectAdded(Closure)");
        getDelegate().whenObjectAdded(action);
    }

    @Override
    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        onMethodCall("whenObjectRemoved(Action)");
        return getDelegate().whenObjectRemoved(action);
    }

    @Override
    public void whenObjectRemoved(Closure action) {
        onMethodCall("whenObjectRemoved(Closure)");
        getDelegate().whenObjectRemoved(action);
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        onMethodCall("withType(Class, Action)");
        return getDelegate().withType(type, configureAction);
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        onMethodCall("withType(Class, Closure)");
        return getDelegate().withType(type, configureClosure);
    }

    @Override
    public void addLater(Provider<? extends T> provider) {
        onMethodCall("addLater(Provider)");
        getDelegate().addLater(provider);
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<T>> provider) {
        onMethodCall("addAllLater(Provider)");
        getDelegate().addAllLater(provider);
    }

    @Override
    public boolean add(T o) {
        onMethodCall("add(Object)");
        return getDelegate().add(o);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        onMethodCall("addAll(Collection)");
        return getDelegate().addAll(c);
    }

    @Override
    public void clear() {
        onMethodCall("clear()");
        getDelegate().clear();
    }

    @Override
    public boolean contains(Object o) {
        onMethodCall("contains(Object)");
        return getDelegate().contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        onMethodCall("containsAll(Collection)");
        return getDelegate().containsAll(c);
    }

    @Override
    public boolean isEmpty() {
        onMethodCall("isEmpty()");
        return getDelegate().isEmpty();
    }

    @Override
    public Iterator<T> iterator() {
        onMethodCall("iterator()");
        return getDelegate().iterator();
    }

    @Override
    public boolean remove(Object o) {
        onMethodCall("remove(Object)");
        return getDelegate().remove(o);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        onMethodCall("removeAll(Collection)");
        return getDelegate().removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        onMethodCall("retainAll(Collection)");
        return getDelegate().retainAll(c);
    }

    @Override
    public int size() {
        onMethodCall("size()");
        return getDelegate().size();
    }

    @Override
    public Object[] toArray() {
        onMethodCall("toArray()");
        return getDelegate().toArray();
    }

    @Override
    public <S> S[] toArray(S[] a) {
        onMethodCall("toArray(Object[])");
        return getDelegate().toArray(a);
    }

    @Override
    @Deprecated
    public Set<T> findAll(Closure spec) {
        onMethodCall("findAll(Closure)");
        return getDelegate().findAll(spec);
    }

    @Override
    public int estimatedSize() {
        onMethodCall("estimatedSize()");
        return ((DomainObjectCollectionInternal<?>) getDelegate()).estimatedSize();
    }

    @Override
    public void beforeCollectionChanges(Action<String> action) {
        onMethodCall("beforeCollectionChanges(Action)");
        ((DomainObjectCollectionInternal<?>) getDelegate()).beforeCollectionChanges(action);
    }

    @Override
    public void disallowChanges() {
        onMethodCall("disallowChanges()");
        getDelegate().disallowChanges();
    }

    @Internal
    @Override
    public String getDisplayName() {
        onMethodCall("getDisplayName()");
        return ((DomainObjectCollectionInternal<?>) getDelegate()).getDisplayName();
    }

}
