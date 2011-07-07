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

import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.listener.ActionBroadcast;

import groovy.lang.Closure;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A domain object collection that presents a combined view of one or more collections.
 * 
 * @param <T> The type of domain objects in the component collections of this collection.
 */
public class CompositeDomainObjectCollection<T> extends AbstractDomainObjectCollection<T> {
    private final Class<T> type;
    private final CompositeStore<T> store;

    public CompositeDomainObjectCollection(Class<T> type, DomainObjectCollection<T>... collections) {
        this(type);
        for (DomainObjectCollection<T> collection : collections) {
            addCollection(collection);
        }
    }

    public CompositeDomainObjectCollection(Class<T> type) {
        this(type, new DefaultCompositeStore<T>());
    }

    protected CompositeDomainObjectCollection(Class<T> type, CompositeStore<T> store) {
        super(store);
        this.store = store;
        this.type = type;
    }
    
    public Class<T> getType() {
        return type;
    }

    public void addCollection(DomainObjectCollection<T> collection) {
        store.addCollection(collection);
    }
    
    public DomainObjectCollection<T> matching(final Spec<? super T> spec) {
        return new CompositeDomainObjectCollection<T>(type, storeWithSpec(spec));
    }

    public DomainObjectCollection<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    public <S extends T> DomainObjectCollection<S> withType(final Class<S> type) {
        return new CompositeDomainObjectCollection<S>(type, storeWithType(type));
    }

    protected CompositeStore<T> storeWithSpec(Spec<? super T> spec) {
        return new FilteredCompositeStore<T>(store, type, spec);
    }

    protected <S extends T> CompositeStore<S> storeWithType(Class<S> type) {
        return new FilteredCompositeStore<S>(store, type, Specs.satisfyAll());
    }

    protected interface CompositeStore<S> extends Store<S> {
        void addCollection(DomainObjectCollection<S> collection);
    }
    
    protected static class DefaultCompositeStore<S> implements CompositeStore<S> {
        private final ActionBroadcast<S> addActions = new ActionBroadcast<S>();
        private final ActionBroadcast<S> removeActions = new ActionBroadcast<S>();
        private final List<DomainObjectCollection<S>> collections = new ArrayList<DomainObjectCollection<S>>();

        public void addCollection(DomainObjectCollection<S> collection) {
            collections.add(collection);
            collection.all(addActions);
            collection.whenObjectRemoved(removeActions);
        }
        
        public Collection<? extends S> getAll() {
            List<S> all = new ArrayList<S>();
            for (DomainObjectCollection<S> collection : collections) {
                all.addAll(collection.getAll());
            }
            return all;
        }

        public void objectAdded(Action<? super S> action) {
            addActions.add(action);
        }

        public void objectRemoved(Action<? super S> action) {
            removeActions.add(action);
        }
    }
    
    protected static class FilteredCompositeStore<S> extends FilteredStore<S> implements CompositeStore<S> {
        public FilteredCompositeStore(CompositeStore<? super S> store, Class<S> type, Spec<? super S> spec) {
            super(store, type, spec);
        }
        
        public void addCollection(DomainObjectCollection<S> collection) {
            throw new UnsupportedOperationException();
        }
    }

}
