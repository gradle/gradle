/*
 * Copyright 2010 the original author or authors.
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
import groovy.lang.MissingPropertyException;
import org.gradle.api.*;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.listener.ActionBroadcast;
import org.gradle.util.ConfigureUtil;

import java.util.*;

public class DefaultNamedDomainObjectContainer<T> extends AbstractDomainObjectCollection<T>
        implements NamedDomainObjectContainer<T> {
    private final DynamicObject dynamicObject = new ContainerDynamicObject();
    private final List<Rule> rules = new ArrayList<Rule>();
    private final ClassGenerator classGenerator;
    private final NamedObjectStore<T> store;
    private final Class<T> type;
    private Set<String> applyingRulesFor = new HashSet<String>();

    public DefaultNamedDomainObjectContainer(Class<T> type, ClassGenerator classGenerator) {
        this(type, classGenerator, new MapStore<T>());
    }
                                         
    public DefaultNamedDomainObjectContainer(Class<T> type, ClassGenerator classGenerator, NamedObjectStore<T> store) {
        super(store);
        this.type = type;
        this.classGenerator = classGenerator;
        this.store = store;
    }

    protected Class<T> getType() {
        return type;
    }

    protected ClassGenerator getClassGenerator() {
        return classGenerator;
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

    protected T findByNameWithoutRules(String name) {
        return store.find(name);
    }

    public NamedDomainObjectCollection<T> matching(Spec<? super T> spec) {
        return classGenerator.newInstance(DefaultNamedDomainObjectContainer.class, type, classGenerator, storeWithSpec(spec));
    }

    public NamedDomainObjectCollection<T> matching(Closure spec) {
        return matching(Specs.convertClosureToSpec(spec));
    }

    public <S extends T> NamedDomainObjectCollection<S> withType(Class<S> type) {
        return classGenerator.newInstance(DefaultNamedDomainObjectContainer.class, type, classGenerator, storeWithType(type));
    }

    protected NamedObjectStore<T> storeWithSpec(Spec<? super T> spec) {
        return new FilteredObjectStore<T>(store, type, spec);
    }

    protected <S extends T> NamedObjectStore<S> storeWithType(Class<S> type) {
        return new FilteredObjectStore<S>(store, type, Specs.satisfyAll());
    }

    public T getByName(String name) throws UnknownDomainObjectException {
        T t = findByName(name);
        if (t == null) {
            throw createNotFoundException(name);
        }
        return t;
    }

    public T getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
        T t = getByName(name);
        ConfigureUtil.configure(configureClosure, t);
        return t;
    }

    public T getAt(String name) throws UnknownDomainObjectException {
        return getByName(name);
    }

    /**
     * Returns a {@link DynamicObject} which can be used to access the domain objects as dynamic properties and
     * methods.
     *
     * @return The dynamic object
     */
    public DynamicObject getAsDynamicObject() {
        return dynamicObject;
    }

    private void applyRules(String name) {
        if (applyingRulesFor.contains(name)) {
            return;
        }
        applyingRulesFor.add(name);
        try {
            for (Rule rule : rules) {
                rule.apply(name);
            }
        } finally {
            applyingRulesFor.remove(name);
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

            @Override
            public String toString() {
                return "Rule: " + description;
            }
        };
        rules.add(rule);
        return rule;
    }

    public List<Rule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownDomainObjectException(String.format("%s with name '%s' not found.", getTypeDisplayName(),
                name));
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
        private final ActionBroadcast<S> addActions = new ActionBroadcast<S>();
        private final ActionBroadcast<S> removeActions = new ActionBroadcast<S>();
        private final Map<String, S> objects = new TreeMap<String, S>();

        public S put(String name, S value) {
            S oldValue = objects.put(name, value);
            if (oldValue != null) {
                removeActions.execute(oldValue);
            }
            addActions.execute(value);
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

    private class ContainerDynamicObject extends CompositeDynamicObject {
        private ContainerDynamicObject() {
            setObjects(new BeanDynamicObject(DefaultNamedDomainObjectContainer.this),
                    new ContainerElementsDynamicObject());
        }

        @Override
        protected String getDisplayName() {
            return DefaultNamedDomainObjectContainer.this.getDisplayName();
        }
    }

    private class ContainerElementsDynamicObject extends AbstractDynamicObject {
        @Override
        protected String getDisplayName() {
            return DefaultNamedDomainObjectContainer.this.getDisplayName();
        }

        @Override
        public boolean hasProperty(String name) {
            return findByName(name) != null;
        }

        @Override
        public T getProperty(String name) throws MissingPropertyException {
            T t = findByName(name);
            if (t == null) {
                return (T) super.getProperty(name);
            }
            return t;
        }

        @Override
        public Map<String, T> getProperties() {
            return getAsMap();
        }

        @Override
        public boolean hasMethod(String name, Object... arguments) {
            return isConfigureMethod(name, arguments);
        }

        @Override
        public Object invokeMethod(String name, Object... arguments) throws groovy.lang.MissingMethodException {
            if (isConfigureMethod(name, arguments)) {
                return ConfigureUtil.configure((Closure) arguments[0], getByName(name));
            } else {
                return super.invokeMethod(name, arguments);
            }
        }

        private boolean isConfigureMethod(String name, Object... arguments) {
            return (arguments.length == 1 && arguments[0] instanceof Closure) && hasProperty(name);
        }
    }
}
