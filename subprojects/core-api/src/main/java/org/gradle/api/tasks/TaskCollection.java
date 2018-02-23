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
package org.gradle.api.tasks;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.specs.Spec;

/**
 * A {@code TaskCollection} contains a set of {@link Task} instances, and provides a number of query methods.
 *
 * @param <T> The type of tasks which this collection contains.
 */
public interface TaskCollection<T extends Task> extends NamedDomainObjectSet<T> {

    /**
     * {@inheritDoc}
     */
    TaskCollection<T> matching(Spec<? super T> spec);

    /**
     * {@inheritDoc}
     */
    TaskCollection<T> matching(Closure closure);

    /**
     * {@inheritDoc}
     */
    T getByName(String name, Closure configureClosure) throws UnknownTaskException;

    /**
     * {@inheritDoc}
     */
    T getByName(String name) throws UnknownTaskException;

    /**
     * {@inheritDoc}
     */
    <S extends T> TaskCollection<S> withType(Class<S> type);

    /**
     * Adds an {@code Action} to be executed when a task is added to this collection.
     *
     * @param action The action to be executed
     * @return the supplied action
     */
    @SuppressWarnings("UnusedDeclaration")
    Action<? super T> whenTaskAdded(Action<? super T> action);

    /**
     * Adds a closure to be called when a task is added to this collection. The task is passed to the closure as the
     * parameter.
     *
     * @param closure The closure to be called
     */
    @SuppressWarnings("UnusedDeclaration")
    void whenTaskAdded(Closure closure);

    /**
     * {@inheritDoc}
     */
    T getAt(String name) throws UnknownTaskException;
}
