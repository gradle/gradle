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

import org.gradle.api.Action;
import org.gradle.internal.Cast;
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.Iterator;

public class ManagedSetModelProjection<M> extends TypeCompatibilityModelProjectionSupport<M> {

    public ManagedSetModelProjection(ModelType<M> type) {
        super(type, true, true);
    }

    @Override
    protected ModelView<M> toView(final MutableModelNode modelNode, final ModelRuleDescriptor ruleDescriptor, final boolean writable) {
        return new ModelView<M>() {

            private boolean closed;

            @Override
            public ModelType<M> getType() {
                return ManagedSetModelProjection.this.getType();
            }

            @Override
            public M getInstance() {
                ModelType<?> elementType = getType().getTypeVariables().get(0);
                return Cast.uncheckedCast(getManagedSetInstance(elementType));
            }

            private <E> ManagedSet<E> getManagedSetInstance(ModelType<E> elementType) {
                return new DelegatingManagedSet<E>(elementType);
            }

            @Override
            public void close() {
                closed = true;
            }

            class DelegatingManagedSet<E> implements ManagedSet<E>, ManagedInstance {

                private final ManagedSet<E> delegate;

                public DelegatingManagedSet(ModelType<E> elementType) {
                    delegate = Cast.uncheckedCast(modelNode.getPrivateData(getType()));
                }

                @Override
                public void create(Action<? super E> action) {
                    if (!writable || closed) {
                        throw new ModelViewClosedException(getType(), ruleDescriptor);
                    }
                    delegate.create(action);
                }

                private void ensureReadable() {
                    if (writable && !closed) {
                        throw new IllegalStateException(String.format("Cannot read contents of element '%s' of type '%s' while it's mutable", modelNode.getPath(), getType(), ruleDescriptor));
                    }
                }

                @Override
                public int size() {
                    ensureReadable();
                    return delegate.size();
                }

                @Override
                public boolean isEmpty() {
                    ensureReadable();
                    return delegate.isEmpty();
                }

                @Override
                public boolean contains(Object o) {
                    ensureReadable();
                    return delegate.contains(o);
                }

                @Override
                public Iterator<E> iterator() {
                    ensureReadable();
                    return delegate.iterator();
                }

                @Override
                public Object[] toArray() {
                    ensureReadable();
                    return delegate.toArray();
                }

                @Override
                public <T> T[] toArray(T[] a) {
                    ensureReadable();
                    return delegate.toArray(a);
                }

                @Override
                public boolean add(E e) {
                    return delegate.add(e);
                }

                @Override
                public boolean remove(Object o) {
                    return delegate.remove(o);
                }

                @Override
                public boolean containsAll(Collection<?> c) {
                    ensureReadable();
                    return delegate.containsAll(c);
                }

                @Override
                public boolean addAll(Collection<? extends E> c) {
                    return delegate.addAll(c);
                }

                @Override
                public boolean retainAll(Collection<?> c) {
                    return delegate.retainAll(c);
                }

                @Override
                public boolean removeAll(Collection<?> c) {
                    return delegate.removeAll(c);
                }

                @Override
                public void clear() {
                    delegate.clear();
                }
            }
        };
    }
}
