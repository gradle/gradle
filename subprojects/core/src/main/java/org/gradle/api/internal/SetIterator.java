/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Sets;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class SetIterator<T> implements Iterator<T> {
    private final Set<T> seen;
    private final Iterator<T> delegate;

    private T next;

    public static <T> Iterator<T> of(Collection<T> collection) {
        if (collection instanceof Set) {
            return collection.iterator();
        } else {
            return wrap(collection.iterator());
        }
    }

    public static <T> Iterator<T> wrap(Iterator<T> delegate) {
        if (delegate instanceof SetIterator) {
            return delegate;
        }
        return new SetIterator<T>(delegate);
    }

    private SetIterator(Iterator<T> delegate) {
        this.delegate = delegate;
        if (delegate instanceof WithEstimatedSize) {
            seen = Sets.newHashSetWithExpectedSize(((WithEstimatedSize) delegate).estimatedSize());
        } else {
            seen = Sets.newHashSet();
        }
        fetchNext();
    }

    private void fetchNext() {
        while (delegate.hasNext()) {
            next = delegate.next();
            if (seen.add(next)) {
                return;
            }
        }
        next = null;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public T next() {
        try {
            return next;
        } finally {
            fetchNext();
        }
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
