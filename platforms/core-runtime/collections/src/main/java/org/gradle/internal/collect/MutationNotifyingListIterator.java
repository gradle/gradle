/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.collect;

import java.util.ListIterator;
import java.util.function.Consumer;

/**
 * A {@link ListIterator} wrapper that notifies a callback when any mutating method is called.
 */
class MutationNotifyingListIterator<E> implements ListIterator<E> {

    private final ListIterator<E> delegate;
    private final Consumer<String> onMutation;

    MutationNotifyingListIterator(ListIterator<E> delegate, Consumer<String> onMutation) {
        this.delegate = delegate;
        this.onMutation = onMutation;
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public E next() {
        return delegate.next();
    }

    @Override
    public boolean hasPrevious() {
        return delegate.hasPrevious();
    }

    @Override
    public E previous() {
        return delegate.previous();
    }

    @Override
    public int nextIndex() {
        return delegate.nextIndex();
    }

    @Override
    public int previousIndex() {
        return delegate.previousIndex();
    }

    @Override
    public void remove() {
        onMutation.accept("listIterator().remove()");
        delegate.remove();
    }

    @Override
    public void set(E e) {
        onMutation.accept("listIterator().set(Object)");
        delegate.set(e);
    }

    @Override
    public void add(E e) {
        onMutation.accept("listIterator().add(Object)");
        delegate.add(e);
    }
}
