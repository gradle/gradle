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
import org.gradle.api.specs.Spec;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class DelegatingDomainObjectSet<T> implements DomainObjectSet<T> {
    private final DomainObjectSet<T> backingSet;

    public DelegatingDomainObjectSet(DomainObjectSet<T> backingSet) {
        this.backingSet = backingSet;
    }

    public DomainObjectSet<T> matching(Closure spec) {
        return backingSet.matching(spec);
    }

    public DomainObjectSet<T> matching(Spec<? super T> spec) {
        return backingSet.matching(spec);
    }

    public <S extends T> DomainObjectSet<S> withType(Class<S> type) {
        return backingSet.withType(type);
    }

    public void all(Action<? super T> action) {
        backingSet.all(action);
    }

    public void all(Closure action) {
        backingSet.all(action);
    }

    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return backingSet.whenObjectAdded(action);
    }

    public void whenObjectAdded(Closure action) {
        backingSet.whenObjectAdded(action);
    }

    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return backingSet.whenObjectRemoved(action);
    }

    public void whenObjectRemoved(Closure action) {
        backingSet.whenObjectRemoved(action);
    }

    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        return backingSet.withType(type, configureAction);
    }

    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return backingSet.withType(type, configureClosure);
    }

    public boolean add(T o) {
        return backingSet.add(o);
    }

    public boolean addAll(Collection<? extends T> c) {
        return backingSet.addAll(c);
    }

    public void clear() {
        backingSet.clear();
    }

    public boolean contains(Object o) {
        return backingSet.contains(o);
    }

    public boolean containsAll(Collection<?> c) {
        return backingSet.containsAll(c);
    }

    public boolean isEmpty() {
        return backingSet.isEmpty();
    }

    public Iterator<T> iterator() {
        return backingSet.iterator();
    }

    public boolean remove(Object o) {
        return backingSet.remove(o);
    }

    public boolean removeAll(Collection<?> c) {
        return backingSet.removeAll(c);
    }

    public boolean retainAll(Collection<?> c) {
        return backingSet.retainAll(c);
    }

    public int size() {
        return backingSet.size();
    }

    public Object[] toArray() {
        return backingSet.toArray();
    }

    public <T> T[] toArray(T[] a) {
        return backingSet.toArray(a);
    }

    public Set<T> findAll(Closure spec) {
        return backingSet.findAll(spec);
    }
}
