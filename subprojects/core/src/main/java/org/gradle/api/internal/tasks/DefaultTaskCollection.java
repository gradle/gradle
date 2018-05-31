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
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.AbstractProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;

import javax.annotation.Nullable;

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
        TaskProvider<T> taskProvider = (TaskProvider<T>) findTask(name);
        if (taskProvider == null) {
            throw createNotFoundException(name);
        }
        return taskProvider;
    }

    @Nullable
    ProviderInternal<? extends Task> findTask(String name) {
        Task task = findByNameWithoutRules(name);
        if (task == null) {
            return findByNameLaterWithoutRules(name);
        }
        return Cast.uncheckedCast(getInstantiator().newInstance(ExistingTaskProvider.class, this, getType(), name, task));
    }

    public static abstract class DefaultTaskProvider<T extends Task> extends AbstractProvider<T> implements Named, TaskProvider<T> {
        final DefaultTaskCollection tasks;
        final Class<T> type;
        final String name;
        boolean removed = false;

        DefaultTaskProvider(DefaultTaskCollection tasks, Class<T> type, String name) {
            this.tasks = tasks;
            this.type = type;
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Class<T> getType() {
            return type;
        }

        @Override
        public boolean isPresent() {
            return tasks.findTask(name) != null;
        }

        @Override
        public void configure(final Action<? super T> action) {
            tasks.configureEach(new Action<Task>() {
                private boolean alreadyExecuted = false;

                @Override
                public void execute(Task task) {
                    // Task specific configuration action should only be executed once
                    if (task.getName().equals(name) && !removed && !alreadyExecuted) {
                        alreadyExecuted = true;
                        action.execute((T)task);
                    }
                }
            });
        }

        @Override
        public String toString() {
            return String.format("provider(task %s, %s)", name, type);
        }
    }

    public static class ExistingTaskProvider<T extends Task> extends DefaultTaskProvider<T> {
        private T task;

        @SuppressWarnings("unused")
        public ExistingTaskProvider(DefaultTaskCollection tasks, Class<T> type, String name, T task) {
            super(tasks, type, name);
            this.task = task;
        }

        @Override
        public boolean isPresent() {
            return task != null;
        }

        @Override
        public T getOrNull() {
            return task;
        }
    }
}
