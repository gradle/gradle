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

package org.gradle.model.internal.core;

import com.google.common.collect.Sets;
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.manage.instance.ManagedInstance;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

@NotThreadSafe
public class DefaultManagedSet<T> implements ManagedSet<T>, ManagedInstance {

    private final Set<T> elements = Sets.newHashSet();
    private final Factory<T> elementFactory;

    public DefaultManagedSet(Factory<T> elementFactory) {
        this.elementFactory = elementFactory;
    }

    public void create(Action<? super T> action) {
        T element = elementFactory.create();
        action.execute(element);
        elements.add(element);
    }

    public int size() {
        return elements.size();
    }

    public boolean isEmpty() {
        return elements.isEmpty();
    }

    public boolean contains(Object o) {
        return elements.contains(o);
    }

    public Iterator<T> iterator() {
        return elements.iterator();
    }

    public Object[] toArray() {
        return elements.toArray();
    }

    public <A> A[] toArray(A[] a) {
        return elements.toArray(a);
    }

    public boolean add(T t) {
        throw new UnsupportedOperationException();
    }

    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(Collection<?> c) {
        return elements.containsAll(c);
    }

    public boolean addAll(Collection<? extends T> c) {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    public void clear() {
        throw new UnsupportedOperationException();
    }
}
