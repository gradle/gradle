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
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.internal.collections.CollectionEventRegister;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.GetPropertyResult;
import org.gradle.internal.metaobject.InvokeMethodResult;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.internal.metaobject.PropertyAccess;
import org.gradle.internal.metaobject.PropertyMixIn;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;

public class DefaultNamedDomainObjectCollection<T> extends DefaultDomainObjectCollection<T> implements NamedDomainObjectCollection<T>, MethodMixIn, PropertyMixIn {

    private final Instantiator instantiator;
    private final Namer<? super T> namer;
    private final Index<T> index;

    private final ContainerElementsDynamicObject elementsDynamicObject = new ContainerElementsDynamicObject();

    private final List<Rule> rules = new ArrayList<Rule>();
    private final Set<String> applyingRulesFor = new HashSet<String>();

    public DefaultNamedDomainObjectCollection(Class<? extends T> type, Collection<T> store, Instantiator instantiator, Namer<? super T> namer) {
        super(type, store);
        this.instantiator = instantiator;
        this.namer = namer;
        this.index = new UnfilteredIndex<T>();
        index();
    }

    protected void index() {
        for (T t : getStore()) {
            index.put(namer.determineName(t), t);
        }
    }

    protected DefaultNamedDomainObjectCollection(Class<? extends T> type, Collection<T> store, CollectionEventRegister<T> eventRegister, Index<T> index, Instantiator instantiator, Namer<? super T> namer) {
        super(type, store, eventRegister);
        this.instantiator = instantiator;
        this.namer = namer;
        this.index = index;
    }

    // should be protected, but use of the class generator forces it to be public
    public DefaultNamedDomainObjectCollection(DefaultNamedDomainObjectCollection<? super T> collection, CollectionFilter<T> filter, Instantiator instantiator, Namer<? super T> namer) {
        this(filter.getType(), collection.filteredStore(filter), collection.filteredEvents(filter), collection.filteredIndex(filter), instantiator, namer);
    }

    /**
     * Subclasses that can guarantee that the backing store enforces name uniqueness should override this to simply call super.add(T) (avoiding an unnecessary lookup)
     */
    public boolean add(T o) {
        if (!hasWithName(namer.determineName(o))) {
            return super.add(o);
        } else {
            handleAttemptToAddItemWithNonUniqueName(o);
            return false;
        }
    }

    @Override
    protected void didAdd(T toAdd) {
        index.put(namer.determineName(toAdd), toAdd);
    }

    @Override
    public void clear() {
        super.clear();
        index.clear();
    }


    @Override
    protected void didRemove(T t) {
        index.remove(namer.determineName(t));
    }


    /**
     * <p>Subclass hook for implementations wanting to throw an exception when an attempt is made to add an item with the same name as an existing item.</p>
     *
     * <p>This implementation does not thrown an exception, meaning that {@code add(T)} will simply return {@code false}.
     *
     * @param o The item that is being attempted to add.
     */
    protected void handleAttemptToAddItemWithNonUniqueName(T o) {
        // do nothing
    }

    /**
     * Asserts that an item with the given name can be added to this collection.
     */
    protected void assertCanAdd(String name) {
        if (hasWithName(name)) {
            throw new InvalidUserDataException(String.format("Cannot add a %s with name '%s' as a %s with that name already exists.", getTypeDisplayName(), name, getTypeDisplayName()));
        }
    }

    /**
     * Asserts that the given item can be added to this collection.
     */
    protected void assertCanAdd(T t) {
        assertCanAdd(getNamer().determineName(t));
    }

    public Namer<T> getNamer() {
        return (Namer) this.namer;
    }

    protected Instantiator getInstantiator() {
        return instantiator;
    }

    protected <S extends T> Index<S> filteredIndex(CollectionFilter<S> filter) {
        return index.filter(filter);
    }

    /**
     * Creates a filtered version of this collection.
     */
    protected <S extends T> DefaultNamedDomainObjectCollection<S> filtered(CollectionFilter<S> filter) {
        return instantiator.newInstance(DefaultNamedDomainObjectCollection.class, this, filter, instantiator, namer);
    }

    public String getDisplayName() {
        return getTypeDisplayName() + " container";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public SortedMap<String, T> getAsMap() {
        return index.asMap();
    }

    public SortedSet<String> getNames() {
        return index.asMap().navigableKeySet();
    }

    public <S extends T> NamedDomainObjectCollection<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    public NamedDomainObjectCollection<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    public NamedDomainObjectCollection<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    public T findByName(String name) {
        T value = findByNameWithoutRules(name);
        if (value != null) {
            return value;
        }
        if (!applyRules(name)) {
            return null;
        }
        return findByNameWithoutRules(name);
    }

    protected boolean hasWithName(String name) {
        return findByNameWithoutRules(name) != null;
    }

    protected T findByNameWithoutRules(String name) {
        return index.get(name);
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

    @Override
    public T getByName(String name, Action<? super T> configureAction) throws UnknownDomainObjectException {
        T t = getByName(name);
        configureAction.execute(t);
        return t;
    }

    public T getAt(String name) throws UnknownDomainObjectException {
        return getByName(name);
    }

    @Override
    public MethodAccess getAdditionalMethods() {
        return getElementsAsDynamicObject();
    }

    @Override
    public PropertyAccess getAdditionalProperties() {
        return getElementsAsDynamicObject();
    }

    protected DynamicObject getElementsAsDynamicObject() {
        return elementsDynamicObject;
    }

    /**
     * @return true if the method _may_ have done some work
     */
    private boolean applyRules(String name) {
        if (rules.isEmpty() || applyingRulesFor.contains(name)) {
            return false;
        }
        applyingRulesFor.add(name);
        try {
            for (Rule rule : rules) {
                rule.apply(name);
            }
        } finally {
            applyingRulesFor.remove(name);
        }
        return true;
    }

    public Rule addRule(Rule rule) {
        rules.add(rule);
        return rule;
    }

    public Rule addRule(final String description, final Closure ruleAction) {
        return addRule(new RuleAdapter(description) {
            @Override
            public void apply(String domainObjectName) {
                ruleAction.call(domainObjectName);
            }
        });
    }

    @Override
    public Rule addRule(final String description, final Action<String> ruleAction) {
        return addRule(new RuleAdapter(description) {
            @Override
            public void apply(String domainObjectName) {
                ruleAction.execute(domainObjectName);
            }
        });
    }

    private static abstract class RuleAdapter implements Rule {

        private final String description;

        RuleAdapter(String description) {
            this.description = description;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "Rule: " + description;
        }
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

    private class ContainerElementsDynamicObject extends AbstractDynamicObject {
        @Override
        public String getDisplayName() {
            return DefaultNamedDomainObjectCollection.this.getDisplayName();
        }

        @Override
        public boolean hasProperty(String name) {
            return findByName(name) != null;
        }

        @Override
        public void getProperty(String name, GetPropertyResult result) {
            T t = findByName(name);
            if (t != null) {
                result.result(t);
            }
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
        public void invokeMethod(String name, InvokeMethodResult result, Object... arguments) {
            if (isConfigureMethod(name, arguments)) {
                result.result(ConfigureUtil.configure((Closure) arguments[0], getByName(name)));
            }
        }

        private boolean isConfigureMethod(String name, Object... arguments) {
            return (arguments.length == 1 && arguments[0] instanceof Closure) && hasProperty(name);
        }
    }

    protected interface Index<T> {
        void put(String name, T value);

        T get(String name);

        void remove(String name);

        void clear();

        NavigableMap<String, T> asMap();

        <S extends T> Index<S> filter(CollectionFilter<S> filter);
    }

    protected static class UnfilteredIndex<T> implements Index<T> {

        private final NavigableMap<String, T> map = new TreeMap<String, T>();

        @Override
        public NavigableMap<String, T> asMap() {
            return map;
        }

        @Override
        public void put(String name, T value) {
            map.put(name, value);
        }

        @Override
        public T get(String name) {
            return map.get(name);
        }

        @Override
        public void remove(String name) {
            map.remove(name);
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public <S extends T> Index<S> filter(CollectionFilter<S> filter) {
            return new FilteredIndex<S>(this, filter);
        }
    }

    private static class FilteredIndex<T> implements Index<T> {

        private final Index<? super T> delegate;
        private final CollectionFilter<T> filter;

        public FilteredIndex(Index<? super T> delegate, CollectionFilter<T> filter) {
            this.delegate = delegate;
            this.filter = filter;
        }

        @Override
        public void put(String name, T value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T get(String name) {
            return filter.filter(delegate.get(name));
        }

        @Override
        public void remove(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public NavigableMap<String, T> asMap() {
            NavigableMap<String, ? super T> delegateMap = delegate.asMap();

            NavigableMap<String, T> filtered = new TreeMap<String, T>();
            for (Map.Entry<String, ? super T> entry : delegateMap.entrySet()) {
                T obj = filter.filter(entry.getValue());
                if (obj != null) {
                    filtered.put(entry.getKey(), obj);
                }
            }

            return filtered;
        }

        @Override
        public <S extends T> Index<S> filter(CollectionFilter<S> filter) {
            return new FilteredIndex<S>(delegate, this.filter.and(filter));
        }
    }

}
