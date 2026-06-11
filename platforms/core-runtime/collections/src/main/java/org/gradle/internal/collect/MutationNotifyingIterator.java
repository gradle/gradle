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

import java.util.Iterator;
import java.util.function.Consumer;

/**
 * An {@link Iterator} wrapper that notifies a callback when {@link #remove()} is called.
 */
class MutationNotifyingIterator<E> implements Iterator<E> {

    private final Iterator<E> delegate;
    private final Consumer<String> onMutation;

    MutationNotifyingIterator(Iterator<E> delegate, Consumer<String> onMutation) {
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
    public void remove() {
        onMutation.accept("iterator().remove()");
        delegate.remove();
    }
}
