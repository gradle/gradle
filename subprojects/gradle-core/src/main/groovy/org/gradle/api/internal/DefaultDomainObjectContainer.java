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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.listener.ActionBroadcast;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultDomainObjectContainer<T> extends AbstractDomainObjectCollection<T> {
    private final Class<T> type;
    private final ObjectStore<T> store;

    public DefaultDomainObjectContainer(Class<T> type) {
        this(type, new SetStore<T>());
    }

    protected DefaultDomainObjectContainer(Class<T> type, ObjectStore<T> store) {
        super(store);
        this.type = type;
        this.store = store;
    }

    public Class<T> getType() {
        return type;
    }

    public DomainObjectCollection<T> matching(final Spec<? super T> spec) {
        return new DefaultDomainObjectContainer<T>(type, storeWithSpec(spec));
    }

    public DomainObjectCollection<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    public <S extends T> DomainObjectCollection<S> withType(final Class<S> type) {
        return new DefaultDomainObjectContainer<S>(type, storeWithType(type));
    }

    protected ObjectStore<T> storeWithSpec(Spec<? super T> spec) {
        return new FilteredObjectStore<T>(store, type, spec);
    }

    protected <S extends T> ObjectStore<S> storeWithType(Class<S> type) {
        return new FilteredObjectStore<S>(store, type, Specs.satisfyAll());
    }

    public void addObject(T value) {
        store.add(value);
    }

    protected interface ObjectStore<S> extends Store<S> {
        void add(S object);
    }

    private static class SetStore<S> implements ObjectStore<S> {
        private final ActionBroadcast<S> addActions = new ActionBroadcast<S>();
        private final ActionBroadcast<S> removeActions = new ActionBroadcast<S>();
        private final Map<S, S> objects = new LinkedHashMap<S, S>();

        public void add(S object) {
            S oldValue = objects.put(object, object);
            if (oldValue != null) {
                removeActions.execute(oldValue);
            }
            addActions.execute(object);
        }

        public Collection<? extends S> getAll() {
            return objects.values();
        }

        public void objectAdded(Action<? super S> action) {
            addActions.add(action);
        }

        public void objectRemoved(Action<? super S> action) {
            removeActions.add(action);
        }
    }

    private static class FilteredObjectStore<S> extends FilteredStore<S> implements ObjectStore<S> {
        public FilteredObjectStore(ObjectStore<? super S> store, Class<S> type, Spec<? super S> spec) {
            super(store, type, spec);
        }

        public void add(S object) {
            throw new UnsupportedOperationException();
        }
    }
}
