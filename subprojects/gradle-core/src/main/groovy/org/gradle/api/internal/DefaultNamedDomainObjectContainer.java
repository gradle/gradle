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
import org.gradle.api.*;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.util.ReflectionUtil;

import java.util.*;

public class DefaultNamedDomainObjectContainer<T> extends AbstractNamedDomainObjectCollection<T> implements
        NamedDomainObjectContainer<T> {
    private final List<Rule> rules = new ArrayList<Rule>();
    private final NamedObjectStore<T> store;
    private final Class<T> type;
    private String applyingRulesFor;

    public DefaultNamedDomainObjectContainer(Class<T> type) {
        this(type, new MapStore<T>());
    }

    protected DefaultNamedDomainObjectContainer(Class<T> type, NamedObjectStore<T> store) {
        super(store);
        this.type = type;
        this.store = store;
    }

    protected Class<T> getType() {
        return type;
    }

    /**
     * Adds a domain object to this container.
     *
     * @param name The name of the domain object.
     * @param object The object to add
     */
    protected void addObject(String name, T object) {
        assert object != null && name != null;
        store.put(name, object);
        ReflectionUtil.installGetter(this, name);
        ReflectionUtil.installConfigureMethod(this, name);
    }

    public String getDisplayName() {
        return String.format("%s container", getTypeDisplayName());
    }

    public Map<String, T> getAsMap() {
        return Collections.unmodifiableMap(store.getAsMap());
    }

    public T findByName(String name) {
        T value = store.find(name);
        if (value != null) {
            return value;
        }
        applyRules(name);
        return store.find(name);
    }

    public NamedDomainObjectCollection<T> matching(final Spec<? super T> spec) {
        return new DefaultNamedDomainObjectContainer<T>(type, storeWithSpec(spec));
    }

    public <S extends T> NamedDomainObjectCollection<S> withType(final Class<S> type) {
        return new DefaultNamedDomainObjectContainer<S>(type, storeWithType(type));
    }

    protected NamedObjectStore<T> storeWithSpec(Spec<? super T> spec) {
        return new FilteredObjectStore<T>(store, type, spec);
    }

    protected <S extends T> NamedObjectStore<S> storeWithType(Class<S> type) {
        return new FilteredObjectStore<S>(store, type, Specs.satisfyAll());
    }

    private void applyRules(String name) {
        if (name.equals(applyingRulesFor)) {
            return;
        }
        applyingRulesFor = name;
        try {
            for (Rule rule : rules) {
                rule.apply(name);
            }
        } finally {
            applyingRulesFor = null;
        }
    }

    public Rule addRule(Rule rule) {
        rules.add(rule);
        return rule;
    }

    public Rule addRule(final String description, final Closure ruleAction) {
        Rule rule = new Rule() {
            public String getDescription() {
                return description;
            }

            public void apply(String taskName) {
                ruleAction.call(taskName);
            }
        };
        rules.add(rule);
        return rule;
    }

    public List<Rule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownDomainObjectException(String.format("%s with name '%s' not found.", getTypeDisplayName(), name));
    }

    protected String getTypeDisplayName() {
        return type.getSimpleName();
    }

    protected interface NamedObjectStore<S> extends Store<S> {
        S put(String name, S value);

        S find(String name);

        Map<String, S> getAsMap();
    }

    private static class MapStore<S> implements NamedObjectStore<S> {
        private final ListenerBroadcast<Action> addActions = new ListenerBroadcast<Action>(Action.class);
        private final ListenerBroadcast<Action> removeActions = new ListenerBroadcast<Action>(Action.class);
        private final Map<String, S> objects = new TreeMap<String, S>();

        public S put(String name, S value) {
            S oldValue = objects.put(name, value);
            if (oldValue != null) {
                removeActions.getSource().execute(oldValue);
            }
            addActions.getSource().execute(value);
            return oldValue;
        }

        public S find(String name) {
            return objects.get(name);
        }

        public Collection<? extends S> getAll() {
            return getAsMap().values();
        }

        public Map<String, S> getAsMap() {
            return objects;
        }

        public void objectAdded(Action<? super S> action) {
            addActions.add(action);
        }

        public void objectRemoved(Action<? super S> action) {
            removeActions.add(action);
        }
    }

    private static class FilteredObjectStore<S> extends FilteredStore<S> implements NamedObjectStore<S> {
        private final NamedObjectStore<? super S> store;

        public FilteredObjectStore(NamedObjectStore<? super S> store, Class<S> type, Spec<? super S> spec) {
            super(store, type, spec);
            this.store = store;
        }

        public S put(String name, S value) {
            throw new UnsupportedOperationException();
        }

        public S find(String name) {
            return filter(store.find(name));
        }

        public Collection<? extends S> getAll() {
            return getAsMap().values();
        }

        public Map<String, S> getAsMap() {
            Map<String, S> filteredMap = new LinkedHashMap<String, S>();
            for (Map.Entry<String, ? super S> entry : store.getAsMap().entrySet()) {
                S s = filter(entry.getValue());
                if (s != null) {
                    filteredMap.put(entry.getKey(), s);
                }
            }
            return filteredMap;
        }
    }
}
