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
import org.gradle.model.ModelViewClosedException;
import org.gradle.model.WriteOnlyModelViewException;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.Iterator;

public class ManagedSetModelProjection<I> extends TypeCompatibilityModelProjectionSupport<ManagedSet<I>> {

    private ManagedSetModelProjection(ModelType<ManagedSet<I>> type) {
        super(type, true, true);
    }

    public static <I> ManagedSetModelProjection<I> of(ModelType<I> elementType) {
        return new ManagedSetModelProjection<I>(new ModelType.Builder<ManagedSet<I>>() {
        }.where(new ModelType.Parameter<I>() {
        }, elementType).build());
    }

    @Override
    protected ModelView<ManagedSet<I>> toView(final MutableModelNode modelNode, final ModelRuleDescriptor ruleDescriptor, final boolean writable) {
        return new ModelView<ManagedSet<I>>() {

            private boolean closed;

            @Override
            public ModelType<ManagedSet<I>> getType() {
                return ManagedSetModelProjection.this.getType();
            }

            @Override
            public ManagedSet<I> getInstance() {
                return new DelegatingManagedSet();
            }

            @Override
            public void close() {
                closed = true;
            }

            class DelegatingManagedSet implements ManagedSet<I>, ManagedInstance {

                private final ManagedSet<I> delegate;

                public DelegatingManagedSet() {
                    delegate = modelNode.getPrivateData(getType());
                }

                @Override
                public void create(Action<? super I> action) {
                    if (!writable || closed) {
                        throw new ModelViewClosedException(getType(), ruleDescriptor);
                    }
                    delegate.create(action);
                }

                private void ensureReadable() {
                    if (writable && !closed) {
                        throw new WriteOnlyModelViewException(getType(), ruleDescriptor);
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
                public Iterator<I> iterator() {
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
                public boolean add(I e) {
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
                public boolean addAll(Collection<? extends I> c) {
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
