/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.internal.HasInternalProtocol;

/**
 * A {@link NamedDomainObjectContainer} that allows to create domain objects with different types.
 *
 * @param <T> the (base) type of domain objects in the container
 */
@Incubating
@HasInternalProtocol
public interface PolymorphicDomainObjectContainer<T> extends NamedDomainObjectContainer<T> {
    /**
     * Creates a domain object with the specified name and type, and adds it to the container.
     *
     * @param name the name of the domain object to be created
     *
     * @param type the type of the domain object to be created
     *
     * @param <U> the type of the domain object to be created
     *
     * @return the created domain object
     *
     * @throws InvalidUserDataException if a domain object with the specified name already exists
     * or the container does not support creating a domain object with the specified type
     */
    <U extends T> U create(String name, Class<U> type) throws InvalidUserDataException;

    /**
     * Looks for an item with the given name and type, creating and adding it to this container if it does not exist.
     *
     * @param name the name of the domain object to be created
     * @param type the type of the domain object to be created
     * @param <U> the type of the domain object to be created
     *
     * @return the found or created domain object, never <code>null</code>.
     *
     * @throws InvalidUserDataException if the container does not support creating a domain object with the specified type
     * @throws ClassCastException if a domain object with the specified name exists with a different type
     */
    @Incubating
    <U extends T> U maybeCreate(String name, Class<U> type) throws InvalidUserDataException;

    /**
     * Creates a domain object with the specified name and type, adds it to the container, and configures
     * it with the specified action.
     *
     * @param name the name of the domain object to be created
     *
     * @param type the type of the domain object to be created
     *
     * @param configuration an action for configuring the domain object
     *
     * @param <U> the type of the domain object to be created
     *
     * @return the created domain object
     *
     * @throws InvalidUserDataException if a domain object with the specified name already exists
     * or the container does not support creating a domain object with the specified type
     */
    <U extends T> U create(String name, Class<U> type, Action<? super U> configuration) throws InvalidUserDataException;

    /**
     * Creates a regular container that wraps the polymorphic container presenting all elements of a specified type.
     *
     * @param type the type of the container elements
     * @param <U> the type of the container elements
     * @return a {@link NamedDomainObjectContainer} providing access to elements of type U.
     */
    <U extends T> NamedDomainObjectContainer<U> containerWithType(Class<U> type);
}
