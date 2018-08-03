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

import com.google.common.base.Function;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.DomainObjectProvider;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.collections.FilteredCollection;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Cast;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.util.ConfigureUtil;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

public class DefaultDomainObjectCollection<T> extends AbstractCollection<T> implements DomainObjectCollection<T>, WithEstimatedSize {

    private final Class<? extends T> type;
    private final ElementSource<T> store;

    private ImmutableActionSet<Void> mutateAction = ImmutableActionSet.empty();

    protected DefaultDomainObjectCollection(Class<? extends T> type, ElementSource<T> store) {
        this.type = type;
        this.store = store;
    }

    protected DefaultDomainObjectCollection(DefaultDomainObjectCollection<? super T> collection, CollectionFilter<T> filter) {
        this(filter.getType(), collection.filteredStore(filter));
    }

    public Class<? extends T> getType() {
        return type;
    }

    protected ElementSource<T> getStore() {
        return store;
    }

    protected CollectionFilter<T> createFilter(Spec<? super T> filter) {
        return createFilter(getType(), filter);
    }

    protected <S extends T> CollectionFilter<S> createFilter(Class<S> type) {
        return new CollectionFilter<S>(type);
    }

    protected <S extends T> CollectionFilter<S> createFilter(Class<? extends S> type, Spec<? super S> spec) {
        return new CollectionFilter<S>(type, spec);
    }

    protected <S extends T> DefaultDomainObjectCollection<S> filtered(CollectionFilter<S> filter) {
        return new DefaultDomainObjectCollection<S>(this, filter);
    }

    protected <S extends T> ElementSource<S> filteredStore(final CollectionFilter<S> filter) {
        return filteredStore(filter, store);
    }

    protected <S extends T> ElementSource<S> filteredStore(CollectionFilter<S> filter, ElementSource<T> elementSource) {
        return new FilteredCollection<T, S>(elementSource, filter);
    }

    public DomainObjectCollection<T> matching(final Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    public DomainObjectCollection<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    public <S extends T> DomainObjectCollection<S> withType(final Class<S> type) {
        return filtered(createFilter(type));
    }

    public Iterator<T> iterator() {
        return store.iterator();
    }

    public void all(Action<? super T> action) {
        store.configureAll(action);
        store.realize();
    }

    @Override
    public void configureEach(Action<? super T> action) {
        store.configureAll(action);
    }

    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        DomainObjectCollection<S> result = withType(type);
        result.all(configureAction);
        return result;
    }

    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        store.addedElement(action);
        return action;
    }

    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        store.removedElement(action);
        return action;
    }

    public boolean add(T toAdd) {
        assertMutable();
        // TODO: How to determine if we've actually added something if nothing is realized?
        Provider<? extends T> provider = store.add(type, Providers.of(toAdd));
        return provider.get() == toAdd;
    }

    @Override
    public void addLater(Provider<? extends T> provider) {
        assertMutable();
        store.add(type, provider);
    }

    public boolean addAll(Collection<? extends T> c) {
        assertMutable();
        boolean changed = false;
        for (T o : c) {
            if (add(o)) {
                changed = true;
            }
        }
        return changed;
    }

    public void clear() {
        assertMutable();
        store.clear();
    }

    public boolean contains(Object o) {
        return store.contains(o);
    }

    public boolean containsAll(Collection<?> c) {
        return store.containsAll(c);
    }

    public boolean isEmpty() {
        return store.isEmpty();
    }

    public boolean remove(Object o) {
        assertMutable();
        return store.remove(o);
    }

    public boolean removeAll(Collection<?> c) {
        assertMutable();
        if (store.constantTimeIsEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Object o : c) {
            if (remove(o)) {
                changed = true;
            }
        }
        return changed;
    }

    public boolean retainAll(Collection<?> target) {
        assertMutable();
        Object[] existingItems = toArray();
        boolean changed = false;
        for (Object existingItem : existingItems) {
            if (!target.contains(existingItem)) {
                remove(existingItem);
                changed = true;
            }
        }
        return changed;
    }

    public int size() {
        return store.size();
    }

    @Override
    public int estimatedSize() {
        return store.estimatedSize();
    }

    public Collection<T> findAll(Closure cl) {
        return findAll(cl, new ArrayList<T>());
    }

    protected <S extends Collection<? super T>> S findAll(Closure cl, S matches) {
        if (store.constantTimeIsEmpty()) {
            return matches;
        }
        for (T t : filteredStore(createFilter(Specs.<Object>convertClosureToSpec(cl)))) {
            matches.add(t);
        }
        return matches;
    }

    // TODO: Replace with decoration
    public void all(Closure action) {
        all(toAction(action));
    }
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return withType(type, toAction(configureClosure));
    }
    public void whenObjectAdded(Closure action) {
        whenObjectAdded(toAction(action));
    }
    public void whenObjectRemoved(Closure action) {
        whenObjectRemoved(toAction(action));
    }

    protected Action<? super T> toAction(Closure action) {
        return ConfigureUtil.configureUsing(action);
    }

    /**
     * Adds an action which is executed before this collection is mutated. Any exception thrown by the action will veto the mutation.
     */
    public void beforeChange(Action<Void> action) {
        mutateAction = mutateAction.add(action);
    }

    protected void assertMutable() {
        mutateAction.execute(null);
    }

    // TODO: toString
    private static class DefaultDomainObjectProvider<T> extends AbstractProvider<T> implements DomainObjectProvider<T> {
        private final Class<T> type;
        private final Provider<? extends T> provider;
        private final DefaultElementSource<T> source;

        private ImmutableActionSet<T> configuration;
        private T object;

        private DefaultDomainObjectProvider(Class<? extends T> type, Provider<? extends T> provider, DefaultElementSource<T> source) {
            this.type = Cast.uncheckedCast(type);
            this.provider = provider;
            this.source = source;
            this.configuration = source.initial;
        }

        @Override
        public void configure(Action<? super T> action) {
            if (object == null) {
                configuration = configuration.add(action);
            } else {
                action.execute(object);
            }
        }

        @Override
        public TypeOf<T> getPublicType() {
            // TODO: Merge with DslObject
            return TypeOf.typeOf(type);
        }

        @Override
        public Class<T> getType() {
            return type;
        }

        @Override
        public T getOrNull() {
            if (object == null) {
                object = provider.get();
                configuration.execute(object);
                configuration = ImmutableActionSet.empty();
                source.realized(object);
                return object;
            }
            // TODO: save & throw failure
            throw new IllegalStateException("could not create something");
        }
    }

    // TODO: How do we maintain Set like behavior?
    public static class DefaultElementSource<T> implements ElementSource<T> {
        private ImmutableActionSet<T> initial = ImmutableActionSet.empty();
        private ImmutableActionSet<T> added = ImmutableActionSet.empty();
        private ImmutableActionSet<T> removed = ImmutableActionSet.empty();

        private final Collection<DomainObjectProvider<? extends T>> backingCollection = Lists.newArrayList();

        public void realize() {
            for (T unused : this) {
                // do nothing
            }
        }

        private void realized(T element) {
            added.execute(element);
        }

        @Override
        public Iterator<T> iterator() {
            return Iterators.transform(backingCollection.iterator(), new Function<DomainObjectProvider<? extends T>, T>() {
                @Override
                public T apply(DomainObjectProvider<? extends T> provider) {
                    return provider.get();
                }
            });
        }

        @Override
        public Iterable<DomainObjectProvider<? extends T>> providers() {
            return new Iterable<DomainObjectProvider<? extends T>>() {
                @Override
                public Iterator<DomainObjectProvider<? extends T>> iterator() {
                    return backingCollection.iterator();
                }
            };
        }

        @Override
        public void configureAll(Action<? super T> action) {
            initial = initial.add(action);
            for (DomainObjectProvider<? extends T> provider : backingCollection) {
                provider.configure(action);
            }
        }

        @Override
        public void addedElement(Action<? super T> action) {
            added = added.add(action);
        }

        @Override
        public void removedElement(Action<? super T> action) {
            removed = removed.add(action);
        }

        @Override
        public boolean constantTimeIsEmpty() {
            return backingCollection.isEmpty();
        }

        @Override
        public int estimatedSize() {
            return size();
        }

        @Override
        public boolean contains(final Object element) {
            return Iterators.contains(iterator(), element);
        }

        @Override
        public boolean containsAll(Collection<?> elements) {
            boolean modified = false;
            for (Object element : elements) {
                if (Iterators.contains(iterator(), element)) {
                    modified = true;
                }
            }
            return modified;
        }

        @Override
        public boolean isEmpty() {
            return backingCollection.isEmpty();
        }

        @Override
        public void clear() {
            for (T object : this) {
                removed.execute(object);
            }
            backingCollection.clear();
        }

        @Override
        public boolean remove(Object o) {
            // TODO: Need to realize entire collection to find an object to remove
            realize();
            return false;
        }

        @Override
        public int size() {
            return backingCollection.size();
        }

        @Override
        public Provider<T> add(Class<? extends T> clazz, Provider<? extends T> provider) {
            DefaultDomainObjectProvider<T> domainObjectProvider = new DefaultDomainObjectProvider<T>(clazz, provider, this);
            backingCollection.add(domainObjectProvider);
            return domainObjectProvider;
        }
    }
}
