/*
 * Copyright 2009 the original author or authors.
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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.collections.EventSubscriptionVerifier;
import org.gradle.api.internal.provider.CollectionProviderInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A domain object collection that presents a combined view of one or more collections.
 *
 * @param <T> The type of domain objects in the component collections of this collection.
 */
public class CompositeDomainObjectSet<T> extends DelegatingDomainObjectSet<T> implements WithEstimatedSize {

    private final Spec<T> uniqueSpec = new ItemIsUniqueInCompositeSpec();
    private final Spec<T> notInSpec = new ItemNotInCompositeSpec();

    private final CollectionCallbackActionDecorator callbackActionDecorator;

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, DomainObjectCollection<? extends T>... collections) {
        return create(type, CollectionCallbackActionDecorator.NOOP, collections);
    }

    @SafeVarargs
    public static <T> CompositeDomainObjectSet<T> create(Class<T> type, CollectionCallbackActionDecorator callbackActionDecorator, DomainObjectCollection<? extends T>... collections) {
        DefaultDomainObjectSet<T> delegate = new DefaultDomainObjectSet<T>(type, new DomainObjectCompositeCollection<T>(), callbackActionDecorator);
        CompositeDomainObjectSet<T> out = new CompositeDomainObjectSet<T>(delegate, callbackActionDecorator);
        for (DomainObjectCollection<? extends T> c : collections) {
            out.addCollection(c);
        }
        return out;
    }

    private CompositeDomainObjectSet(DefaultDomainObjectSet<T> delegate, CollectionCallbackActionDecorator callbackActionDecorator) {
        super(delegate);
        this.callbackActionDecorator = callbackActionDecorator;
    }

    public class ItemIsUniqueInCompositeSpec implements Spec<T> {
        @Override
        public boolean isSatisfiedBy(T element) {
            int matches = 0;
            for (DomainObjectCollection<? extends T> collection : getStore().store) {
                if (collection.contains(element)) {
                    if (++matches > 1) {
                        return false;
                    }
                }
            }

            return true;
        }
    }

    public class ItemNotInCompositeSpec implements Spec<T> {
        @Override
        public boolean isSatisfiedBy(T element) {
            return !getStore().contains(element);
        }
    }

    @Override
    protected DefaultDomainObjectSet<T> getDelegate() {
        return (DefaultDomainObjectSet<T>) super.getDelegate();
    }

    @SuppressWarnings("unchecked")
    protected DomainObjectCompositeCollection<T> getStore() {
        return (DomainObjectCompositeCollection) getDelegate().getStore();
    }

    @Override
    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return super.whenObjectAdded(Actions.filter(action, uniqueSpec));
    }

    @Override
    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return super.whenObjectRemoved(Actions.filter(action, notInSpec));
    }

    public void addCollection(DomainObjectCollection<? extends T> collection) {
        if (!getStore().containsCollection(collection)) {
            getStore().addComposited(collection);
            collection.all(new InternalAction<T>() {
                @Override
                public void execute(T t) {
                    getDelegate().getEventRegister().fireObjectAdded(t);
                }
            });
            collection.whenObjectRemoved(new Action<T>() {
                @Override
                public void execute(T t) {
                    getDelegate().getEventRegister().fireObjectRemoved(t);
                }
            });
        }
    }

    public void removeCollection(DomainObjectCollection<? extends T> collection) {
        getStore().removeComposited(collection);
        for (T item : collection) {
            getDelegate().getEventRegister().fireObjectRemoved(item);
        }
    }

    @SuppressWarnings({"NullableProblems", "unchecked"})
    @Override
    public Iterator<T> iterator() {
        return getStore().iterator();
    }

    @Override
    @SuppressWarnings("unchecked")
    /**
     * This method is expensive. Avoid calling it if possible. If all you need is a rough
     * estimate, call {@link #estimatedSize()} instead.
     */
    public int size() {
        return getStore().size();
    }

    @Override
    public int estimatedSize() {
        return getStore().estimatedSize();
    }

    @Override
    public void all(Action<? super T> action) {
        //calling overloaded method with extra behavior:
        whenObjectAdded(action);
        for (T t : this) {
            callbackActionDecorator.decorate(action).execute(t);
        }
    }

    // TODO Make this work with pending elements
    private final static class DomainObjectCompositeCollection<T> implements ElementSource<T> {

        private final List<DomainObjectCollection<? extends T>> store = Lists.newLinkedList();

        public boolean containsCollection(DomainObjectCollection<? extends T> collection) {
            for (DomainObjectCollection<? extends T> ts : store) {
                if (ts == collection) {
                    return true;
                }
            }
            return false;
        }

        Set<T> collect() {
            if (store.isEmpty()) {
                return Collections.emptySet();
            }
            Set<T> tmp = Sets.newLinkedHashSetWithExpectedSize(estimatedSize());
            for (DomainObjectCollection<? extends T> collection : store) {
                tmp.addAll(collection);
            }
            return tmp;
        }

        @Override
        public int size() {
            return collect().size();
        }

        @Override
        public boolean isEmpty() {
            for (DomainObjectCollection<? extends T> ts : store) {
                if (!ts.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean contains(Object o) {
            for (DomainObjectCollection<? extends T> ts : store) {
                if (ts.contains(o)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Iterator<T> iterator() {
            if (store.isEmpty()) {
                return Collections.emptyIterator();
            }
            if (store.size() == 1) {
                return (Iterator<T>) store.get(0).iterator();
            }
            return collect().iterator();
        }

        @Override
        public boolean add(T t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        public void addComposited(DomainObjectCollection<? extends T> collection) {
            this.store.add(collection);
        }

        public void removeComposited(DomainObjectCollection<? extends T> collection) {
            Iterator<DomainObjectCollection<? extends T>> iterator = store.iterator();
            while (iterator.hasNext()) {
                DomainObjectCollection<? extends T> next = iterator.next();
                if (next == collection) {
                    iterator.remove();
                    break;
                }
            }
        }

        @Override
        public boolean constantTimeIsEmpty() {
            return store.isEmpty();
        }

        @Override
        public int estimatedSize() {
            int size = 0;
            for (DomainObjectCollection<? extends T> ts : store) {
                size += Estimates.estimateSizeOf(ts);
            }
            return size;
        }

        @Override
        public Iterator<T> iteratorNoFlush() {
            return iterator();
        }

        @Override
        public void realizePending() {

        }

        @Override
        public void realizePending(Class<?> type) {

        }

        @Override
        public boolean addPending(ProviderInternal<? extends T> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removePending(ProviderInternal<? extends T> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addPendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removePendingCollection(CollectionProviderInternal<T, ? extends Iterable<T>> provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onPendingAdded(Action<T> action) {

        }

        @Override
        public void setSubscriptionVerifier(EventSubscriptionVerifier<T> immediateRealizationSpec) {

        }

        @Override
        public void realizeExternal(ProviderInternal<? extends T> provider) {

        }

        @Override
        public MutationGuard getMutationGuard() {
            return MutationGuards.identity();
        }
    }
}
