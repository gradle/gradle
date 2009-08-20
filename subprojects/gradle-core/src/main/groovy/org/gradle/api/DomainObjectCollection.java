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
package org.gradle.api;

import groovy.lang.Closure;
import org.gradle.api.specs.Spec;

import java.util.Map;
import java.util.Set;

/**
 * A {@code DomainObjectCollection} represents a read-only set of domain objects of type {@code T}.
 *
 * <p>The object in a collection are accessable as read-only properties of the collection, using the name of the object
 * as the property name. For example:</p>
 *
 * <pre>
 * tasks.add('myTask')
 * tasks.myTask.dependsOn someOtherTask
 * </pre>
 *
 * <p>A dynamic method is added for each object which takes a configuration closure. This is equivalent to calling
 * {@link #getByName(String, groovy.lang.Closure)}. For example:</p>
 *
 * <pre>
 * tasks.add('myTask')
 * tasks.myTask {
 *     dependsOn someOtherTask
 * }
 * </pre>
 *
 * <p>You can also use the {@code []} operator to access the objects of a collection by name. For example:</p>
 *
 * <pre>
 * tasks.add('myTask')
 * tasks['myTask'].dependsOn someOtherTask
 * </pre>
 *
 * @param <T> The type of domain objects in this collection.
 */
public interface DomainObjectCollection<T> extends Iterable<T> {
    /**
     * Returns the objects in this collection.
     *
     * @return The objects. Returns an empty set if this collection is empty.
     */
    Set<T> getAll();

    /**
     * Returns the objects in this collection, as a map from object name to object instance.
     *
     * @return The objects. Returns an empty map if this collection is empty.
     */
    Map<String, T> getAsMap();

    /**
     * Locates an object by name, returning null if there is no such object.
     *
     * @param name The object name
     * @return The object with the given name, or null if there is no such object in this collection.
     */
    T findByName(String name);

    /**
     * Locates an object by name, failing if there is no such object.
     *
     * @param name The object name
     * @return The object with the given name. Never returns null.
     * @throws UnknownDomainObjectException when there is no such object in this collection.
     */
    T getByName(String name) throws UnknownDomainObjectException;

    /**
     * Locates an object by name, failing if there is no such object. The given configure closure is executed against
     * the object before it is returned from this method. The object is passed to the closure as it's delegate.
     *
     * @param name The object name
     * @param configureClosure The closure to use to configure the object.
     * @return The object with the given name, after the configure closure has been applied to it. Never returns null.
     * @throws UnknownDomainObjectException when there is no such object in this collection.
     */
    T getByName(String name, Closure configureClosure) throws UnknownDomainObjectException;

    /**
     * Locates an object by name, failing if there is no such task. This method is identical to {@link
     * #getByName(String)}. You can call this method in your build script by using the groovy {@code []} operator.
     *
     * @param name The object name
     * @return The object with the given name. Never returns null.
     * @throws UnknownDomainObjectException when there is no such object in this collection.
     */
    T getAt(String name) throws UnknownDomainObjectException;

    /**
     * Returns the objects in this collection which meet the given specification.
     *
     * @param spec The specification to use.
     * @return The matching objects. Returns an empty set if there are no such objects in this collection.
     */
    Set<T> findAll(Spec<? super T> spec);

    /**
     * Returns a collection containing the objects in this collection of the given type.  The returned collection is
     * live, so that when matching objects are later added to this collection, they are also visible in the filtered
     * collection.
     *
     * @param type The type of objects to find.
     * @return The matching objects. Returns an empty set if there are no such objects in this collection.
     */
    <S extends T> DomainObjectCollection<S> withType(Class<S> type);

    /**
     * Returns a collection which contains the objects in this collection which meet the given specification. The
     * returned collection is live, so that when matching objects are added to this collection, they are also visible in
     * the filtered collection.
     *
     * @param spec The specification to use.
     * @return The collection of matching objects. Returns an empty collection if there are no such objects in this
     *         collection.
     */
    DomainObjectCollection<T> matching(Spec<? super T> spec);

    /**
     * Adds an {@code Action} to be executed when an object is added to this collection.
     *
     * @param action The action to be executed
     * @return the supplied action
     */
    Action<? super T> whenObjectAdded(Action<? super T> action);

    /**
     * Adds a closure to be called when an object is added to this collection. The newly added object is passed to the
     * closure as the parameter.
     *
     * @param action The closure to be called
     */
    void whenObjectAdded(Closure action);

    /**
     * Adds an {@code Action} to be executed when an object is removed from this collection.
     *
     * @param action The action to be executed
     * @return the supplied action
     */
    Action<? super T> whenObjectRemoved(Action<? super T> action);

    /**
     * Executes the given action against all objects in this collection, and any objects subsequently added to this
     * collection.
     *
     * @param action The action to be executed
     */
    void allObjects(Action<? super T> action);

    /**
     * Executes the given closure against all objects in this collection, and any objects subsequently added to this
     * collection.
     *
     * @param action The closure to be called
     */
    void allObjects(Closure action);
}
