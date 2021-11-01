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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The special-cased implementation of {@link Set} that tracks all accesses to its elements.
 * @param <E> the type of elements
 */
class AccessTrackingSet<E> extends AbstractSet<E> {
    // TODO(https://github.com/gradle/configuration-cache/issues/337) Only a limited subset of entrySet/keySet methods are currently tracked.
    private final Set<? extends E> delegate;
    private final Consumer<Object> onAccess;

    public AccessTrackingSet(Set<? extends E> delegate, Consumer<Object> onAccess) {
        this.delegate = delegate;
        this.onAccess = onAccess;
    }

    @Override
    public boolean contains(@Nullable Object o) {
        boolean result = delegate.contains(o);
        onAccess.accept(o);
        return result;
    }

    @Override
    public boolean containsAll(@Nonnull Collection<?> collection) {
        boolean result = delegate.containsAll(collection);
        for (Object o : collection) {
            onAccess.accept(o);
        }
        return result;
    }

    @Override
    public Iterator<E> iterator() {
        return new AbstractIterator<E>() {
            private final Iterator<? extends E> inner = delegate.iterator();

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
