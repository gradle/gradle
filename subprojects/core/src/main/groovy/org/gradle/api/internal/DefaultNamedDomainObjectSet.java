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
import org.gradle.util.ConfigureUtil;

import java.util.*;

import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.CollectionEventRegister;

public class DefaultNamedDomainObjectSet<T> extends DefaultDomainObjectSet<T>
        implements NamedDomainObjectContainer<T> {
    private final DynamicObject dynamicObject = new ContainerDynamicObject();
    private final List<Rule> rules = new ArrayList<Rule>();
    private final ClassGenerator classGenerator;
    private Set<String> applyingRulesFor = new HashSet<String>();
    private final Namer<? super T> namer;

    public DefaultNamedDomainObjectSet(Class<T> type, ClassGenerator classGenerator, Namer<? super T> namer) {
        super(type, new TreeSet(new NamerComparator<T>(namer)));
        this.classGenerator = classGenerator;
        this.namer = namer;
    }

    protected DefaultNamedDomainObjectSet(Class<T> type, Set<T> store, ClassGenerator classGenerator, Namer<? super T> namer) {
        super(type, store);
        this.classGenerator = classGenerator;
        this.namer = namer;
    }
    
    protected DefaultNamedDomainObjectSet(Class<T> type, Set<T> store, CollectionEventRegister<T> eventRegister, ClassGenerator classGenerator, Namer<? super T> namer) {
        super(type, store, eventRegister);
        this.classGenerator = classGenerator;
        this.namer = namer;
    }
    
    // should be protected, but use of teh class generator forces it to be public
    public DefaultNamedDomainObjectSet(DefaultNamedDomainObjectSet<? super T> collection, CollectionFilter<T> filter, ClassGenerator classGenerator, Namer<? super T> namer) {
        this(filter.getType(), collection.filteredStore(filter), collection.filteredEvents(filter), classGenerator, namer);
    }
    
    protected <S extends T> DefaultNamedDomainObjectSet<S> filtered(CollectionFilter<S> filter) {
        return classGenerator.newInstance(DefaultNamedDomainObjectSet.class, this, filter, classGenerator, namer);
    }
    
    protected ClassGenerator getClassGenerator() {
        return classGenerator;
    }

    public String getDisplayName() {
        return String.format("%s container", getTypeDisplayName());
    }

    public Map<String, T> getAsMap() {
        Map<String, T> map = new TreeMap<String, T>();
        for (T o : getStore()) {
            map.put(namer.determineName(o), o);
        }
        return map;
    }

    public <S extends T> NamedDomainObjectSet<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    public NamedDomainObjectSet<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    public NamedDomainObjectSet<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }
    
    public T findByName(String name) {
        T value = findByNameWithoutRules(name);
        if (value != null) {
            return value;
        }
        applyRules(name);
        return findByNameWithoutRules(name);
    }

    protected T findByNameWithoutRules(String name) {
        for (T o : getStore()) {
            if (name.equals(namer.determineName(o))) {
                return o;
            }
        }
        return null;
    }

    protected T removeByName(String name) {
        T it = getByName(name);
        if (it != null) {
            if (remove(it)) {
                return it;
            } else {
                // unclear what the best thing to do here would be
                throw new IllegalStateException(String.format("found '%s' with name '%s' but remove() returned false", it, name));
            }
        } else {
            return null;
        }
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
        return getType().getSimpleName();
    }

    private class ContainerDynamicObject extends CompositeDynamicObject {
        private ContainerDynamicObject() {
            setObjects(new BeanDynamicObject(DefaultNamedDomainObjectSet.this),
                    new ContainerElementsDynamicObject());
        }

        @Override
        protected String getDisplayName() {
            return DefaultNamedDomainObjectSet.this.getDisplayName();
        }
    }

    private class ContainerElementsDynamicObject extends AbstractDynamicObject {
        @Override
        protected String getDisplayName() {
            return DefaultNamedDomainObjectSet.this.getDisplayName();
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
