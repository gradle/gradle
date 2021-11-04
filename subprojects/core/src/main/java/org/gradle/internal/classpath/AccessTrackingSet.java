/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.classpath;

import com.google.common.collect.AbstractIterator;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

class AccessTrackingSet<E> extends AbstractSet<E> {
    // TODO(https://github.com/gradle/configuration-cache/issues/337) Only a limited subset of entrySet/keySet methods are currently tracked.
    private final Set<E> delegate;
    private final Consumer<E> onAccess;

    @SuppressWarnings("unchecked")
    public AccessTrackingSet(Set<? extends E> delegate, Consumer<? super E> onAccess) {
        // The delegate set is not modified in this class, so it is safe to "downcast" its items to E.
        this.delegate = (Set<E>) delegate;
        // Consumer consumes, so upcast is safe there.
        this.onAccess = (Consumer<E>) onAccess;
    }

    @Override
    public Iterator<E> iterator() {
        return new AbstractIterator<E>() {
            private final Iterator<E> inner = delegate.iterator();

            @Override
            protected E computeNext() {
                if (!inner.hasNext()) {
                    return endOfData();
                }
                E value = inner.next();
                onAccess.accept(value);
                return value;
            }
        };
    }

    @Override
    public int size() {
        return delegate.size();
    }
}
