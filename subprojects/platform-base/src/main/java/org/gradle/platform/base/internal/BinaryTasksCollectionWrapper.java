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

package org.gradle.platform.base.internal;

import groovy.lang.Closure;
import org.gradle.api.*;
import org.gradle.api.specs.Spec;
import org.gradle.platform.base.BinaryTasksCollection;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class BinaryTasksCollectionWrapper implements BinaryTasksCollection {
    private final BinaryTasksCollection delegate;

    public BinaryTasksCollectionWrapper(BinaryTasksCollection delegate) {
        this.delegate = delegate;
    }

    public <T extends Task> T findSingleTaskWithType(Class<T> type) {
        DomainObjectSet<T> tasks = withType(type);
        if (tasks.size() == 0) {
            return null;
        }
        if (tasks.size() > 1) {
            throw new UnknownDomainObjectException(String.format("Multiple tasks with type '%s' found.", type.getSimpleName()));
        }
        return tasks.iterator().next();
    }

    @Override
    public String taskName(String verb) {
        return delegate.taskName(verb);
    }

    @Override
    public String taskName(String verb, String object) {
        return delegate.taskName(verb, object);
    }

    @Override
    public Task getBuild() {
        return delegate.getBuild();
    }

    @Override
    public Task getCheck() {
        return delegate.getCheck();
    }

    @Override
    public <T extends Task> void create(String name, Class<T> type, Action<? super T> config) {
        delegate.create(name, type, config);
    }

    @Override
    public <S extends Task> DomainObjectSet<S> withType(Class<S> type) {
        return delegate.withType(type);
    }

    @Override
    public DomainObjectSet<Task> matching(Spec<? super Task> spec) {
        return delegate.matching(spec);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public DomainObjectSet<Task> matching(Closure spec) {
        return delegate.matching(spec);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Set<Task> findAll(Closure spec) {
        return delegate.findAll(spec);
    }

    @Override
    public <S extends Task> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        return delegate.withType(type, configureAction);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public <S extends Task> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return delegate.withType(type, configureClosure);
    }

    @Override
    public Action<? super Task> whenObjectAdded(Action<? super Task> action) {
        return delegate.whenObjectAdded(action);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void whenObjectAdded(Closure action) {
        delegate.whenObjectAdded(action);
    }

    @Override
    public Action<? super Task> whenObjectRemoved(Action<? super Task> action) {
        return delegate.whenObjectRemoved(action);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void whenObjectRemoved(Closure action) {
        delegate.whenObjectRemoved(action);
    }

    @Override
    public void all(Action<? super Task> action) {
        delegate.all(action);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void all(Closure action) {
        delegate.all(action);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<Task> iterator() {
        return delegate.iterator();
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
    public boolean add(Task task) {
        return delegate.add(task);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Task> c) {
        return delegate.addAll(c);
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
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

}
