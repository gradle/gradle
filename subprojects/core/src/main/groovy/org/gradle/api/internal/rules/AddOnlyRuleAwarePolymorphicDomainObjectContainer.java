/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.rules;

import org.gradle.internal.reflect.Instantiator;

import java.util.Collection;
import java.util.Iterator;

public abstract class AddOnlyRuleAwarePolymorphicDomainObjectContainer<T> extends RuleAwarePolymorphicDomainObjectContainer<T> {
    public AddOnlyRuleAwarePolymorphicDomainObjectContainer(Class<T> type, Instantiator instantiator) {
        super(type, instantiator);
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("This collection does not support element removal.");
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException("This collection does not support element removal.");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("This collection does not support element removal.");
    }

    @Override
    public boolean retainAll(Collection<?> target) {
        throw new UnsupportedOperationException("This collection does not support element removal.");
    }

    @Override
    public Iterator<T> iterator() {
        return new RemovalPreventingDelegatingIterator<T>(super.iterator());
    }

    private static class RemovalPreventingDelegatingIterator<T> implements Iterator<T> {

        private final Iterator<T> delegate;

        public RemovalPreventingDelegatingIterator(Iterator<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public T next() {
            return delegate.next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("This iterator does not support removal.");
        }
    }
}
