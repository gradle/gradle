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

/**
 * <p>A {@code NamedDomainObjectCollection} represents a read-only set of domain objects of type {@code T}. Each domain
 * object in this collection has a name, which uniquely identifies the object in this collection.</p>
 *
 * <p>Each object in a collection are accessible as read-only properties of the collection, using the name of the object
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
public interface NamedDomainObjectCollection<T> extends DomainObjectCollection<T> {
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
     * {@inheritDoc}
     */
    <S extends T> NamedDomainObjectCollection<S> withType(Class<S> type);

    /**
     * {@inheritDoc}
     */
    NamedDomainObjectCollection<T> matching(Spec<? super T> spec);

    /**
     * {@inheritDoc}
     */
    NamedDomainObjectCollection<T> matching(Closure spec);
}
