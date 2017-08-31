/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes;

import com.google.common.collect.Iterators;
import org.gradle.internal.Cast;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

final class ImmutableModuleExclusionSet implements Set<AbstractModuleExclusion> {
    private final Set<AbstractModuleExclusion> delegate;

    final AbstractModuleExclusion[] elements;
    private final int hashCode;

    ImmutableModuleExclusionSet(Set<AbstractModuleExclusion> delegate) {
        this.delegate = delegate;
        this.elements = delegate.toArray(new AbstractModuleExclusion[0]);
        this.hashCode = delegate.hashCode();
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
    public Iterator<AbstractModuleExclusion> iterator() {
        return Iterators.forArray(elements);
    }

    @Override
    public Object[] toArray() {
        return elements;
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return Cast.uncheckedCast(elements);
    }

    @Override
    public boolean add(AbstractModuleExclusion abstractModuleExclusion) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends AbstractModuleExclusion> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImmutableModuleExclusionSet that = (ImmutableModuleExclusionSet) o;

        if (hashCode != that.hashCode) {
            return false;
        }

        return delegate.equals(that.delegate);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
