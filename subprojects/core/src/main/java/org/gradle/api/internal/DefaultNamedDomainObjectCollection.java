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

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.collections.CollectionEventRegister;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.ElementSource;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.Cast;
import org.gradle.internal.metaobject.AbstractDynamicObject;
import org.gradle.internal.metaobject.DynamicInvokeResult;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.metaobject.MethodAccess;
import org.gradle.internal.metaobject.MethodMixIn;
import org.gradle.internal.metaobject.PropertyAccess;
import org.gradle.internal.metaobject.PropertyMixIn;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;

public class DefaultNamedDomainObjectCollection<T> extends DefaultDomainObjectCollection<T> implements NamedDomainObjectCollection<T>, MethodMixIn, PropertyMixIn {

    private final Instantiator instantiator;
    private final Namer<? super T> namer;
    private final ContainerElementsDynamicObject elementsDynamicObject = new ContainerElementsDynamicObject();
    private final List<Rule> rules = new ArrayList<Rule>();
    private final Set<String> applyingRulesFor = new HashSet<String>();

    private final Index<T> index = null;

    private interface Index<T> {
        NamedDomainObjectProvider<T> find(String name);
        SortedMap<String, NamedDomainObjectProvider<T>> asMap();
        Provider<T> add(String name, Class<? extends T> clazz, Provider<? extends T> provider);
    }

    public DefaultNamedDomainObjectCollection(Class<? extends T> type, ElementSource<T> store, Instantiator instantiator, Namer<? super T> namer) {
        super(type, store);
        this.instantiator = instantiator;
        this.namer = namer;
    }

    protected DefaultNamedDomainObjectCollection(Class<? extends T> type, ElementSource<T> store, CollectionEventRegister<T> eventRegister, Index<T> index, Instantiator instantiator, Namer<? super T> namer) {
        super(type, store);
        this.instantiator = instantiator;
        this.namer = namer;
    }

    // should be protected, but use of the class generator forces it to be public
    public DefaultNamedDomainObjectCollection(DefaultNamedDomainObjectCollection<? super T> collection, CollectionFilter<T> filter, Instantiator instantiator, Namer<? super T> namer) {
        this(filter.getType(), collection.filteredStore(filter), collection.filteredEvents(filter), collection.filteredIndex(filter), instantiator, namer);
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

    protected Instantiator getInstantiator() {
        return instantiator;
    }

    /**
     * Creates a filtered version of this collection.
     */
    protected <S extends T> DefaultNamedDomainObjectCollection<S> filtered(CollectionFilter<S> filter) {
        return instantiator.newInstance(DefaultNamedDomainObjectCollection.class, this, filter, instantiator, namer);
    }

    @Override
    public boolean add(T e) {
        Provider<T> provider = index.add(namer.determineName(e), getType(), Providers.of(e));
        return provider.get() == e;
    }

    @Override
    public boolean remove(Object o) {
        return false;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return false;
    }

    @Override
    public void clear() {

    }

    @Override
    public void addLater(Provider<? extends T> provider) {
        // index.add(provider.getName(), provider.getType(), provider);
    }

    @Override
    public Namer<T> getNamer() {
        return (Namer) this.namer;
    }

    public String getDisplayName() {
        return getTypeDisplayName() + " container";
    }

    protected String getTypeDisplayName() {
        return getType().getSimpleName();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public SortedMap<String, T> getAsMap() {
        return Maps.transformValues(index.asMap(), new Function<NamedDomainObjectProvider<T>, T>() {
            @Override
            public T apply(NamedDomainObjectProvider<T> provider) {
                return provider.get();
            }
        });
    }

    @Override
    public SortedSet<String> getNames() {
        return Sets.newTreeSet(index.asMap().keySet());
    }

    @Override
    public <S extends T> NamedDomainObjectCollection<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    @Override
    public NamedDomainObjectCollection<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    @Override
    public NamedDomainObjectCollection<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    @Override
    public T findByName(String name) {
        NamedDomainObjectProvider<? extends T> provider = findDomainObject(name);
        if (provider!=null) {
            return provider.get();
        }
        return null;
    }

    @Override
    public T getByName(String name) throws UnknownDomainObjectException {
        return named(name).get();
    }

    @Override
    public T getByName(String name, Closure configureClosure) throws UnknownDomainObjectException {
        return getByName(name, toAction(configureClosure));
    }

    @Override
    public T getByName(String name, Action<? super T> configureAction) throws UnknownDomainObjectException {
        NamedDomainObjectProvider<T> provider = named(name);
        provider.configure(configureAction);
        return provider.get();
    }

    @Override
    public T getAt(String name) throws UnknownDomainObjectException {
        return getByName(name);
    }

    @Override
    public NamedDomainObjectProvider<T> named(String name) throws UnknownTaskException {
        NamedDomainObjectProvider<? extends T> provider = findDomainObject(name);
        if (provider == null) {
            throw new UnknownDomainObjectException(String.format("%s with name '%s' not found.", getTypeDisplayName(),
                name));
        }
        return Cast.uncheckedCast(provider);
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

    @Override
    public NamedDomainObjectCollectionSchema getCollectionSchema() {
        return new NamedDomainObjectCollectionSchema() {
            @Override
            public Iterable<? extends NamedDomainObjectSchema> getElements() {
                // TODO: make the provider implement the schema?
                return Iterables.transform(index.asMap().values(), new Function<NamedDomainObjectProvider<? extends T>, NamedDomainObjectSchema>() {
                    @Override
                    public NamedDomainObjectSchema apply(final NamedDomainObjectProvider<? extends T> provider) {
                        return new NamedDomainObjectSchema() {
                            @Override
                            public String getName() {
                                return provider.getName();
                            }

                            @Override
                            public TypeOf<?> getPublicType() {
                                return provider.getPublicType();
                            }
                        };
                    }
                });
            }
        };
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

    @Override
    public Rule addRule(Rule rule) {
        rules.add(rule);
        return rule;
    }

    @Override
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

    @Override
    public List<Rule> getRules() {
        return Collections.unmodifiableList(rules);
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
        public DynamicInvokeResult tryGetProperty(String name) {
            T t = findByName(name);
            return t == null ? DynamicInvokeResult.notFound() : DynamicInvokeResult.found(t);
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
        public DynamicInvokeResult tryInvokeMethod(String name, Object... arguments) {
            if (isConfigureMethod(name, arguments)) {
                return DynamicInvokeResult.found(ConfigureUtil.configure((Closure) arguments[0], getByName(name)));
            }
            return DynamicInvokeResult.notFound();
        }

        private boolean isConfigureMethod(String name, Object... arguments) {
            return (arguments.length == 1 && arguments[0] instanceof Closure) && hasProperty(name);
        }
    }

    @Nullable
    protected NamedDomainObjectProvider<? extends T> findDomainObject(String name) {
        NamedDomainObjectProvider<T> provider = index.find(name);
        if (provider == null) {
            if (applyRules(name)) {
                provider = index.find(name);
            }
        }

        return provider;
    }

    protected abstract class AbstractNamedDomainObjectProvider<I extends T> extends AbstractProvider<I> implements Named, NamedDomainObjectProvider<I> {
        private final String name;

        protected AbstractNamedDomainObjectProvider(String name) {
            this.name = name;
        }

        @Nullable
        @Override
        public Class<I> getType() {
            return (Class<I>) DefaultNamedDomainObjectCollection.this.getType();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public boolean isPresent() {
            return findDomainObject(name) != null;
        }

        @Override
        public String toString() {
            return String.format("provider(%s %s, %s)", getTypeDisplayName(), getName(), getType());
        }
    }
}
