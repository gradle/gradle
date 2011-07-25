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
import org.gradle.api.*;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.util.DeprecationLogger;
import java.util.Set;

import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.collections.CollectionEventRegister;

public class DefaultTaskCollection<T extends Task> extends DefaultNamedDomainObjectSet<T> implements TaskCollection<T> {
    protected final ProjectInternal project;

    public DefaultTaskCollection(Class<T> type, ClassGenerator classGenerator, ProjectInternal project) {
        super(type, classGenerator, new Task.Namer());
        this.project = project;
    }

    protected DefaultTaskCollection(Class<T> type, Set<T> store, CollectionEventRegister<T> eventRegister, ClassGenerator classGenerator, ProjectInternal project) {
        super(type, store, eventRegister, classGenerator, new Task.Namer());
        this.project = project;
    }

    public DefaultTaskCollection(DefaultTaskCollection<? super T> collection, CollectionFilter<T> filter, ClassGenerator classGenerator, ProjectInternal project) {
        this(filter.getType(), collection.filteredStore(filter), collection.filteredEvents(filter), classGenerator, project);
    }

    protected <S extends T> DefaultTaskCollection<S> filtered(CollectionFilter<S> filter) {
        return getClassGenerator().newInstance(DefaultTaskCollection.class, this, filter, getClassGenerator(), project);
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

    public void allTasks(Action<? super T> action) {
        DeprecationLogger.nagUser("TaskCollection.allTasks()", "all()");
        all(action);
    }

    public void allTasks(Closure action) {
        DeprecationLogger.nagUser("TaskCollection.allTasks()", "all()");
        all(action);
    }

    @Override
    public String getTypeDisplayName() {
        return "task";
    }

    @Override
    protected UnknownDomainObjectException createNotFoundException(String name) {
        return new UnknownTaskException(String.format("Task with name '%s' not found in %s.", name, project));
    }
}
