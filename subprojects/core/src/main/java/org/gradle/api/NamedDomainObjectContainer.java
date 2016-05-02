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
import org.gradle.util.Configurable;

/**
 * <p>A named domain object container is a specialisation of {@link NamedDomainObjectSet} that adds the ability to create
 * instances of the element type.</p>
 * 
 * <p>Implementations may use different strategies for creating new object instances.</p>
 * 
 * <p>Note that a container is an implementation of {@link java.util.SortedSet}, which means that the container is guaranteed
 * to only contain elements with unique names within this container. Furthermore, items are ordered by their name.</p>
 * 
 * @param <T> The type of domain objects in this container.
 * @see NamedDomainObjectSet
 */
public interface NamedDomainObjectContainer<T> extends NamedDomainObjectSet<T>, Configurable<NamedDomainObjectContainer<T>> {

    /**
     * Creates a new item with the given name, adding it to this container.
     *
     * @param name The name to assign to the created object
     * @return The created object. Never null.
     * @throws InvalidUserDataException if an object with the given name already exists in this container.
     */
    T create(String name) throws InvalidUserDataException;

    /**
     * Looks for an item with the given name, creating and adding it to this container if it does not exist.
     *
     * @param name The name to find or assign to the created object
     * @return The found or created object. Never null.
     */
    T maybeCreate(String name);

    /**
     * Creates a new item with the given name, adding it to this container, then configuring it with the given closure.
     *
     * @param name The name to assign to the created object
     * @param configureClosure The closure to configure the created object with
     * @return The created object. Never null.
     * @throws InvalidUserDataException if an object with the given name already exists in this container.
     */
    T create(String name, Closure configureClosure) throws InvalidUserDataException;

    /**
     * Creates a new item with the given name, adding it to this container, then configuring it with the given action.
     *
     * @param name The name to assign to the created object
     * @param configureAction The action to configure the created object with
     * @return The created object. Never null.
     * @throws InvalidUserDataException if an object with the given name already exists in this container.
     */
    T create(String name, Action<? super T> configureAction) throws InvalidUserDataException;

    /**
     * <p>Allows the container to be configured, creating missing objects as they are referenced.</p>
     * 
     * <p>TODO: example usage</p>
     * 
     * @param configureClosure The closure to configure this container with
     * @return This.
     */
    NamedDomainObjectContainer<T> configure(Closure configureClosure);
    
}
