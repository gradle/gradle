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
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.util.ListenerBroadcast;

import java.util.*;

public class DefaultDomainObjectContainer<T> extends AbstractDomainObjectCollection<T> implements DomainObjectContainer<T> {
    private final ListenerBroadcast<Action> addActions = new ListenerBroadcast<Action>(Action.class);
    private final ListenerBroadcast<Action> removeActions = new ListenerBroadcast<Action>(Action.class);
    private final Map<String, T> objects = new TreeMap<String, T>();
    private final List<Rule> rules = new ArrayList<Rule>();
    private final Class<T> type;
    private String applyingRulesFor;

    public DefaultDomainObjectContainer(Class<T> type) {
        this.type = type;
    }

    /**
     * Adds a domain object to this container.
     *
     * @param name The name of the domain object.
     * @param object The object to add
     */
    protected void addObject(String name, T object) {
        T oldValue = objects.put(name, object);
        if (oldValue != null) {
            removeActions.getSource().execute(oldValue);
        }
        addActions.getSource().execute(object);
    }

    public String getDisplayName() {
        return "domain object container";
    }

    public Map<String, T> getAsMap() {
        return Collections.unmodifiableMap(objects);
    }

    public T findByName(String name) {
        if (!objects.containsKey(name)) {
            applyRules(name);
        }
        return objects.get(name);
    }

    public DomainObjectCollection<T> matching(final Spec<? super T> spec) {
        return new FilteredContainer<T>(this, type, spec);
    }

    public <S extends T> DomainObjectCollection<S> withType(final Class<S> type) {
        return new FilteredContainer<S>(this, type, Specs.satisfyAll());
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

    public List<Rule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        addActions.add(action);
        return action;
    }

    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        removeActions.add(action);
        return action;
    }

    public void whenObjectAdded(Closure action) {
        addActions.add("execute", action);
    }

    /**
     * Called when an unknown domain object is requested.
     *
     * @param name The name of the unknonw object
     * @return The exception to throw.
     */
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownDomainObjectException(String.format("Domain object with name '%s' not found.", name));
    }

    protected static class FilteredContainer<S> extends AbstractDomainObjectCollection<S> {
        private final AbstractDomainObjectCollection<? super S> parent;
        private final Class<S> type;
        private final Spec<? super S> spec;

        public FilteredContainer(AbstractDomainObjectCollection<? super S> parent, Class<S> type, Spec<? super S> spec) {
            this.parent = parent;
            this.type = type;
            this.spec = spec;
        }

        public S findByName(String name) {
            return filter(parent.findByName(name));
        }

        public Map<String, S> getAsMap() {
            Map<String, S> filteredMap = new LinkedHashMap<String, S>();
            for (Map.Entry<String, ? super S> entry : parent.getAsMap().entrySet()) {
                S s = filter(entry.getValue());
                if (s != null) {
                    filteredMap.put(entry.getKey(), s);
                }
            }
            return filteredMap;
        }

        private S filter(Object object) {
            if (!type.isInstance(object)) {
                return null;
            }
            S s = type.cast(object);
            if (!spec.isSatisfiedBy(s)) {
                return null;
            }
            return s;
        }

        public DomainObjectCollection<S> matching(Spec<? super S> spec) {
            return new FilteredContainer<S>(this, type, spec);
        }

        public <U extends S> DomainObjectCollection<U> withType(Class<U> type) {
            return new FilteredContainer<U>(this, type, Specs.SATISFIES_ALL);
        }

        public Action<? super S> whenObjectAdded(final Action<? super S> action) {
            parent.whenObjectAdded(new Action<Object>() {
                public void execute(Object t) {
                    S s = filter(t);
                    if (s != null) {
                        action.execute(s);
                    }
                }
            });
            return action;
        }

        public void whenObjectAdded(final Closure action) {
            parent.whenObjectAdded(new Action<Object>() {
                public void execute(Object t) {
                    S s = filter(t);
                    if (s != null) {
                        action.call(s);
                    }
                }
            });
        }

        public Action<? super S> whenObjectRemoved(Action<? super S> action) {
            throw new UnsupportedOperationException();
        }

        public String getDisplayName() {
            return parent.getDisplayName();
        }

        protected UnknownDomainObjectException createNotFoundException(String name) {
            return parent.createNotFoundException(name);
        }
    }
}
