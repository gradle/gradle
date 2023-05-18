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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.DelegatingNamedDomainObjectSet;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.core.ModelNode;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.type.ModelType;

import java.util.concurrent.atomic.AtomicBoolean;

public class DefaultRealizableTaskCollection<T extends Task> extends DelegatingNamedDomainObjectSet<T> implements TaskCollection<T>, TaskDependencyContainer {

    private final Class<T> type;
    private final AtomicBoolean realized = new AtomicBoolean();
    private final MutableModelNode modelNode;
    private final Instantiator instantiator;

    public DefaultRealizableTaskCollection(Class<T> type, TaskCollection<T> delegate, MutableModelNode modelNode, Instantiator instantiator) {
        super(delegate);
        assert !(delegate instanceof DefaultRealizableTaskCollection) : "Attempt to wrap already realizable task collection in realizable wrapper: " + delegate;

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

    @Override
    protected TaskCollection<T> getDelegate() {
        return (TaskCollection<T>) super.getDelegate();
    }

    private <S extends T> TaskCollection<S> realizable(Class<S> type, TaskCollection<S> collection) {
        return Cast.uncheckedCast(instantiator.newInstance(DefaultRealizableTaskCollection.class, type, collection, modelNode, instantiator));
    }

    @Override
    public TaskCollection<T> matching(Spec<? super T> spec) {
        return realizable(type, getDelegate().matching(spec));
    }

    @Override
    public TaskCollection<T> matching(Closure closure) {
        return realizable(type, getDelegate().matching(closure));
    }

    @Override
    public <S extends T> TaskCollection<S> withType(Class<S> type) {
        return realizable(type, getDelegate().withType(type));
    }

    @Override
    public Action<? super T> whenTaskAdded(Action<? super T> action) {
        return getDelegate().whenTaskAdded(action);
    }

    @Override
    public void whenTaskAdded(Closure closure) {
        getDelegate().whenTaskAdded(closure);
    }

    @Override
    public TaskProvider<T> named(String name) throws InvalidUserDataException {
        return getDelegate().named(name);
    }

    @Override
    public TaskProvider<T> named(String name, Action<? super T> configurationAction) throws UnknownTaskException {
        return getDelegate().named(name, configurationAction);
    }

    @Override
    public <S extends T> TaskProvider<S> named(String name, Class<S> type) throws UnknownTaskException {
        return getDelegate().named(name, type);
    }

    @Override
    public <S extends T> TaskProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownTaskException {
        return getDelegate().named(name, type, configurationAction);
    }
}
