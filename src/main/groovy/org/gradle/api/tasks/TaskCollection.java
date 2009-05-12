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

import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.Action;
import org.gradle.api.specs.Spec;

import java.util.Set;
import java.util.Map;

import groovy.lang.Closure;

/**
 * A {@code TaskCollection} contains a set of {@link Task} instances, and provides a number of query methods.
 *
 * <p>The tasks in a collection are accessable as read-only properties of the collection, using the name of the task as
 * the property name. For example:</p>
 *
 * <pre>
 * tasks.add('myTask')
 * tasks.myTask.dependsOn someOtherTask
 * </pre>
 *
 * <p>A dynamic method is added for each task which takes a configuration closure. This is equivalent to calling {@link
 * #getByName(String, groovy.lang.Closure)}. For example:</p>
 *
 * <pre>
 * tasks.add('myTask')
 * tasks.myTask {
 *     dependsOn someOtherTask
 * }
 * </pre>
 */
public interface TaskCollection<T extends Task> extends Iterable<T> {
    /**
     * Returns the tasks in this collection.
     *
     * @return The tasks. Returns an empty set if this collection is empty.
     */
    Set<T> getAll();

    /**
     * Returns the tasks in this collection, as a map from task name to {@code Task} instance.
     *
     * @return The tasks. Returns an empty map if this collection is empty.
     */
    Map<String, T> getAsMap();

    /**
     * Returns the tasks in this collection which meet the given specification.
     *
     * @param spec The specification to use.
     * @return The matching tasks. Returns an empty set if there are no such tasks in this collection.
     */
    Set<T> findAll(Spec<? super T> spec);

    /**
     * Returns a collection which contains the tasks in this collection which meet the given specification. The returned
     * collection is live, so that when matching tasks are added to this collection, they are also visible in the
     * filtered collection.
     *
     * @param spec The specification to use.
     * @return The collection of matching tasks. Returns an empty collection if there are no such tasks in this
     *         collection.
     */
    TaskCollection<T> matching(Spec<? super T> spec);

    /**
     * Locates a task by name, returning null if there is no such task.
     *
     * @param name The task name
     * @return The task with the given name, or null if there is no such task in this collection.
     */
    T findByName(String name);

    /**
     * Locates a task by name, failing if there is no such task. The given configure closure is executed against the
     * task before it is returned from this method.
     *
     * @param name The task name
     * @param configureClosure The closure to use to configure the task.
     * @return The task with the given name. Never returns null.
     * @throws org.gradle.api.UnknownTaskException when there is no such task in this collection.
     */
    T getByName(String name, Closure configureClosure) throws UnknownTaskException;

    /**
     * Locates a task by name, failing if there is no such task.
     *
     * @param name The task name
     * @return The task with the given name. Never returns null.
     * @throws org.gradle.api.UnknownTaskException when there is no such task in this collection.
     */
    T getByName(String name) throws UnknownTaskException;

    /**
     * Returns a collection containing the tasks in this collection of the given type.  The returned collection is live,
     * so that when matching tasks are added to this collection, they are also visible in the filtered collection.
     *
     * @param type The type of tasks to find.
     * @return The matching tasks. Returns an empty set if there are no such tasks in this collection.
     */
    <S extends T> TaskCollection<S> withType(Class<S> type);

    /**
     * Adds an {@code Action} to be executed when a task is added to this collection.
     *
     * @param action The action to be executed
     * @return the supplied action
     */
    Action<? super T> whenTaskAdded(Action<? super T> action);

    /**
     * Adds a closure to be called when a task is added to this collection. The task is passed to the closure as the
     * parameter.
     *
     * @param closure The closure to be called
     */
    void whenTaskAdded(Closure closure);

    /**
     * Executes the given action against all tasks in this collection, and any tasks subsequently added to this
     * collection.
     *
     * @param action The action to be executed
     */
    void allTasks(Action<? super T> action);

    /**
     * Executes the given closure against all tasks in this collection, and any tasks subsequently added to this
     * collection.
     *
     * @param closure The closure to be called
     */
    void allTasks(Closure closure);

    /**
     * Locates a task by name, failing if there is no such task. This method is identical to {@link #getByName(String)}.
     * You can call this method in your build script by using the groovy {@code []} operator:
     *
     * <pre>
     * tasks['some-task'].dependsOn 'another-task'
     * </pre>
     *
     * @param name The task name
     * @return The task with the given name. Never returns null.
     * @throws org.gradle.api.UnknownTaskException when there is no such task in this collection.
     */
    T getAt(String name) throws UnknownTaskException;
}
