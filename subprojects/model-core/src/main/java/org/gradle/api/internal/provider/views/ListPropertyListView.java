/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.provider.views;

import org.gradle.api.provider.ListProperty;

import javax.annotation.concurrent.NotThreadSafe;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of List, that is used for Property upgrades
 */
@NotThreadSafe
public class ListPropertyListView<E> extends AbstractList<E> {

    private final ListProperty<E> delegate;

    public ListPropertyListView(ListProperty<E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public E get(int index) {
        return delegate.get().get(index);
    }

    @Override
    public E set(int index, E element) {
        List<E> list = new ArrayList<>(delegate.get());
        E replaced = list.set(index, element);
        delegate.set(list);
        return replaced;
    }

    @Override
    public boolean add(E element) {
        delegate.add(element);
        return true;
    }

    @Override
    public void add(int index, E element) {
        List<E> list = new ArrayList<>(delegate.get());
        list.add(index, element);
        delegate.set(list);
    }

    @Override
    public E remove(int index) {
        List<E> list = new ArrayList<>(delegate.get());
        E removed = list.remove(index);
        delegate.set(list);
        return removed;
    }

    @Override
    public int size() {
        return delegate.get().size();
    }
}
