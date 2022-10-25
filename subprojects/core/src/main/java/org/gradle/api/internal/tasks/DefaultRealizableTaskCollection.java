/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.Namer;
import org.gradle.api.Rule;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.type.ModelType;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultRealizableTaskCollection<T extends Task> implements TaskCollection<T>, TaskDependencyContainer {

    private final TaskCollection<T> delegate;
    private final Class<T> type;
    private final AtomicBoolean realized = new AtomicBoolean();
    private final MutableModelNode modelNode;
    private final Instantiator instantiator;

    public DefaultRealizableTaskCollection(Class<T> type, TaskCollection<T> delegate, MutableModelNode modelNode, Instantiator instantiator) {
        assert !(delegate instanceof DefaultRealizableTaskCollection) : "Attempt to wrap already realizable task collection in realizable wrapper: " + delegate;

        this.delegate = delegate;
        this.type = type;
        this.modelNode = modelNode;
        this.instantiator = instantiator;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        // Task dependencies may be calculated more than once.
        // This guard is purely an optimisation.
        if (modelNode != null && realized.compareAndSet(false, true)) {
            modelNode.ensureAtLeast(ModelNode.State.SelfClosed);
            for (MutableModelNode node : modelNode.getLinks(ModelType.of(type))) {
                node.ensureAtLeast(ModelNode.State.GraphClosed);
            }
        }
        for (T t : this) {
            context.add(t);
        }
    }

    private <S extends T> TaskCollection<S> realizable(Class<S> type, TaskCollection<S> collection) {
        return Cast.uncheckedCast(instantiator.newInstance(DefaultRealizableTaskCollection.class, type, collection, modelNode, instantiator));
    }

    @Override
    public TaskCollection<T> matching(Spec<? super T> spec) {
        return realizable(type, delegate.matching(spec));
    }

    @Override
    public TaskCollection<T> matching(Closure closure) {
        return realizable(type, delegate.matching(closure));
    }

    @Override
    public T getByName(String name, Closure configureClosure) throws UnknownTaskException {
        return delegate.getByName(name, configureClosure);
    }

    @Override
    public T getByName(String name, Action<? super T> configureAction) throws UnknownTaskException {
        return delegate.getByName(name, configureAction);
    }

    @Override
    public T getByName(String name) throws UnknownTaskException {
        return delegate.getByName(name);
    }

    @Override
    public <S extends T> TaskCollection<S> withType(Class<S> type) {
        return realizable(type, delegate.withType(type));
    }

    @Override
    public Action<? super T> whenTaskAdded(Action<? super T> action) {
        return delegate.whenTaskAdded(action);
    }

    @Override
    public void whenTaskAdded(Closure closure) {
        delegate.whenTaskAdded(closure);
    }

    @Override
    public T getAt(String name) throws UnknownTaskException {
        return delegate.getAt(name);
    }

    @Override
    public Set<T> findAll(Closure spec) {
        return delegate.findAll(spec);
    }

    @Override
    public boolean add(T e) {
        return delegate.add(e);
    }

    @Override
    public void addLater(Provider<? extends T> provider) {
        delegate.addLater(provider);
    }

    @Override
    public void addAllLater(Provider<? extends Iterable<T>> provider) {
        delegate.addAllLater(provider);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return delegate.addAll(c);
    }

    @Override
    public Namer<T> getNamer() {
        return delegate.getNamer();
    }

    @Override
    public SortedMap<String, T> getAsMap() {
        return delegate.getAsMap();
    }

    @Override
    public SortedSet<String> getNames() {
        return delegate.getNames();
    }

    @Override
    public T findByName(String name) {
        return delegate.findByName(name);
    }

    @Override
    public Rule addRule(Rule rule) {
        return delegate.addRule(rule);
    }

    @Override
    public Rule addRule(String description, Closure ruleAction) {
        return delegate.addRule(description, ruleAction);
    }

    @Override
    public Rule addRule(String description, Action<String> ruleAction) {
        return delegate.addRule(description, ruleAction);
    }

    @Override
    public List<Rule> getRules() {
        return delegate.getRules();
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Action<? super S> configureAction) {
        return realizable(type, (DefaultTaskCollection<S>) delegate.withType(type, configureAction));
    }

    @Override
    public <S extends T> DomainObjectCollection<S> withType(Class<S> type, Closure configureClosure) {
        return realizable(type, (DefaultTaskCollection<S>) delegate.withType(type, configureClosure));
    }

    @Override
    public Action<? super T> whenObjectAdded(Action<? super T> action) {
        return delegate.whenObjectAdded(action);
    }

    @Override
    public void whenObjectAdded(Closure action) {
        delegate.whenObjectAdded(action);
    }

    @Override
    public Action<? super T> whenObjectRemoved(Action<? super T> action) {
        return delegate.whenObjectRemoved(action);
    }

    @Override
    public void whenObjectRemoved(Closure action) {
        delegate.whenObjectRemoved(action);
    }

    @Override
    public void all(Action<? super T> action) {
        delegate.all(action);
    }

    @Override
    public void all(Closure action) {
        delegate.all(action);
    }

    @Override
    public void configureEach(Action<? super T> action) {
        delegate.configureEach(action);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <R> R[] toArray(R[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public TaskProvider<T> named(String name) throws InvalidUserDataException {
        return delegate.named(name);
    }

    @Override
    public TaskProvider<T> named(String name, Action<? super T> configurationAction) throws UnknownTaskException {
        return delegate.named(name, configurationAction);
    }

    @Override
    public <S extends T> TaskProvider<S> named(String name, Class<S> type) throws UnknownTaskException {
        return delegate.named(name, type);
    }

    @Override
    public <S extends T> TaskProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownTaskException {
        return delegate.named(name, type, configurationAction);
    }

    @Override
    public NamedDomainObjectCollectionSchema getCollectionSchema() {
        return delegate.getCollectionSchema();
    }
}
