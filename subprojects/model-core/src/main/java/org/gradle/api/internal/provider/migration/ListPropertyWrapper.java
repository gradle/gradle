/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.provider.migration;

import org.gradle.api.provider.ListProperty;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class ListPropertyWrapper<T> extends AbstractList<T> {

    private final ListProperty<T> delegate;

    public ListPropertyWrapper(ListProperty<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get(int index) {
        return delegate.get().get(index);
    }

    @Override
    public int size() {
        return delegate.get().size();
    }

    @Override
    public T set(int index, T element) {
        List<T> newList = new ArrayList<>(delegate.get());
        T result = newList.set(index, element);
        delegate.set(newList);
        return result;
    }

    @Override
    public void add(int index, T element) {
        List<T> newList = new ArrayList<>(delegate.get());
        delegate.set(newList);
    }

    @Override
    public T remove(int index) {
        List<T> newList = new ArrayList<>(delegate.get());
        T removed = newList.remove(index);
        delegate.set(newList);
        return removed;
    }
}
