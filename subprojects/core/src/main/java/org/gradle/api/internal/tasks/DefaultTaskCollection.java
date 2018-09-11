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
package org.gradle.api.internal.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;

public class DefaultTaskCollection<T extends Task> extends DefaultNamedDomainObjectSet<T> implements TaskCollection<T> {
    private static final Task.Namer NAMER = new Task.Namer();

    protected final ProjectInternal project;

    public DefaultTaskCollection(Class<T> type, Instantiator instantiator, ProjectInternal project) {
        super(type, instantiator, NAMER);
        this.project = project;
    }

    public DefaultTaskCollection(DefaultTaskCollection<? super T> collection, CollectionFilter<T> filter, Instantiator instantiator, ProjectInternal project) {
        super(collection, filter, instantiator, NAMER);
        this.project = project;
    }

    protected <S extends T> DefaultTaskCollection<S> filtered(CollectionFilter<S> filter) {
        return getInstantiator().newInstance(DefaultTaskCollection.class, this, filter, getInstantiator(), project);
    }

    @Override
    public <S extends T> TaskCollection<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    @Override
    public TaskCollection<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    @Override
    public TaskCollection<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    public Action<? super T> whenTaskAdded(Action<? super T> action) {
        return whenObjectAdded(action);
    }

    public void whenTaskAdded(Closure closure) {
        whenObjectAdded(closure);
    }

    @Override
    public String getTypeDisplayName() {
        return "task";
    }

    @Override
    protected UnknownTaskException createNotFoundException(String name) {
        return new UnknownTaskException(String.format("Task with name '%s' not found in %s.", name, project));
    }

    @Override
    public TaskProvider<T> named(String name) throws UnknownTaskException {
        return (TaskProvider<T>) super.named(name);
    }

    @Override
    protected TaskProvider<? extends T> createExistingProvider(String name, T object) {
        TaskInternal taskInternal = (TaskInternal) object;
        TaskIdentity<?> taskIdentity = taskInternal.getTaskIdentity();
        return Cast.uncheckedCast(getInstantiator().newInstance(ExistingTaskProvider.class, this, object, taskIdentity));
    }

    protected abstract class DefaultTaskProvider<I extends Task> extends AbstractProvider<I> implements Named, TaskProvider<I> {

        final TaskIdentity<I> identity;
        boolean removed = false;

        DefaultTaskProvider(TaskIdentity<I> identity) {
            this.identity = identity;
        }

        @Override
        public String getName() {
            return identity.name;
        }

        @Override
        public Class<I> getType() {
            return identity.type;
        }

        @Override
        public boolean isPresent() {
            return findDomainObject(identity.name) != null;
        }

        @Override
        public String toString() {
            return String.format("provider(task %s, %s)", identity.name, identity.type);
        }
    }

    // Cannot be private due to reflective instantiation
    public class ExistingTaskProvider<I extends T> extends DefaultTaskProvider<I> {

        private final I task;

        public ExistingTaskProvider(I task, TaskIdentity<I> identity) {
            super(identity);
            this.task = task;
        }

        @Override
        public void configure(Action<? super I> action) {
            action.execute(task);
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        public I getOrNull() {
            return task;
        }
    }
}
