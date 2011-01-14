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
import org.gradle.api.internal.DefaultNamedDomainObjectContainer;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.util.DeprecationLogger;

public class DefaultTaskCollection<T extends Task> extends DefaultNamedDomainObjectContainer<T> implements TaskCollection<T> {
    protected final ProjectInternal project;

    public DefaultTaskCollection(Class<T> type, ClassGenerator classGenerator, ProjectInternal project) {
        super(type, classGenerator);
        this.project = project;
    }

    public DefaultTaskCollection(Class<T> type, ClassGenerator classGenerator, ProjectInternal project, NamedObjectStore<T> store) {
        super(type, classGenerator, store);
        this.project = project;
    }

    @Override
    public TaskCollection<T> matching(Spec<? super T> spec) {
        return getClassGenerator().newInstance(DefaultTaskCollection.class, getType(), getClassGenerator(), project, storeWithSpec(spec));
    }

    @Override
    public TaskCollection<T> matching(Closure spec) {
        return matching(Specs.convertClosureToSpec(spec));
    }

    @Override
    public <S extends T> TaskCollection<S> withType(Class<S> type) {
        return getClassGenerator().newInstance(DefaultTaskCollection.class, type, getClassGenerator(), project, storeWithType(type));
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
