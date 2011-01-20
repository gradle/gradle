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
import org.gradle.util.ConfigureUtil;
import org.gradle.util.DeprecationLogger;

import java.util.*;

public abstract class AbstractDomainObjectCollection<T> implements DomainObjectCollection<T> {
    private final Store<T> store;

    protected AbstractDomainObjectCollection(Store<T> store) {
        this.store = store;
    }

    public Set<T> getAll() {
        return new LinkedHashSet<T>(store.getAll());
    }

    public Set<T> findAll(Spec<? super T> spec) {
        return Specs.filterIterable(store.getAll(), spec);
    }

    public Iterator<T> iterator() {
        return getAll().iterator();
    }

    public void allObjects(Action<? super T> action) {
        DeprecationLogger.nagUser("DomainObjectCollection.allObjects()", "all()");
        all(action);
    }

    public void allObjects(Closure action) {
        DeprecationLogger.nagUser("DomainObjectCollection.allObjects()", "all()");
        all(action);
    }

    public void all(Action<? super T> action) {
        whenObjectAdded(action);
        for (T t : new ArrayList<T>(store.getAll())) {
            action.execute(t);
        }
    }

    public void all(Closure action) {
        all(toAction(action));
    }

    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        DomainObjectCollection<S> result = withType(type);
        result.all(configureAction);
        return result;
    }

    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        DomainObjectCollection<S> result = withType(type);
        result.all(configureClosure);
        return result;
    }

    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        store.objectAdded(action);
        return action;
    }

    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        store.objectRemoved(action);
        return action;
    }

    public void whenObjectAdded(Closure action) {
        whenObjectAdded(toAction(action));
    }

    private Action<? super T> toAction(final Closure action) {
        return new Action<T>() {
            public void execute(T t) {
                ConfigureUtil.configure(action, t);
            }
        };
    }

    protected interface Store<S> {
        Collection<? extends S> getAll();

        void objectAdded(Action<? super S> action);

        void objectRemoved(Action<? super S> action);
    }

    protected static class FilteredStore<S> implements Store<S> {
        private final Store<? super S> store;
        private final Class<S> type;
        private final Spec<? super S> spec;

        public FilteredStore(Store<? super S> store, Class<S> type, Spec<? super S> spec) {
            this.store = store;
            this.type = type;
            this.spec = spec;
        }

        public Collection<? extends S> getAll() {
            List<S> values = new ArrayList<S>();
            for (Object s : store.getAll()) {
                S filtered = filter(s);
                if (filtered != null) {
                    values.add(filtered);
                }
            }
            return values;
        }

        public void objectAdded(Action<? super S> action) {
            store.objectAdded(filter(action));
        }

        public void objectRemoved(Action<? super S> action) {
            store.objectRemoved(filter(action));
        }

        protected S filter(Object object) {
            if (!type.isInstance(object)) {
                return null;
            }
            S s = type.cast(object);
            if (!spec.isSatisfiedBy(s)) {
                return null;
            }
            return s;
        }

        protected Action<Object> filter(final Action<? super S> action) {
            return new Action<Object>() {
                public void execute(Object object) {
                    S s = filter(object);
                    if (s != null) {
                        action.execute(s);
                    }
                }
            };
        }
    }

}
