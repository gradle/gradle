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

import org.gradle.api.provider.Provider;
import org.gradle.internal.HasInternalProtocol;

/**
 * A {@link NamedDomainObjectContainer} that allows you to create objects with different types.
 *
 * @param <T> the (base) type of objects in the container
 */
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

    /**
     * Defines a new object, which will be created and configured when it is required. A object is 'required' when the object is located using query methods such as {@link #getByName(String)} or when {@link Provider#get()} is called on the return value of this method.
     *
     * <p>It is generally more efficient to use this method instead of {@link #create(String, Class, Action)} or {@link #create(String, Class)}, as those methods will eagerly create and configure the object, regardless of whether that object is required for the current build or not. This method, on the other hand, will defer creation and configuration until required.</p>
     *
     * @param name The name of the object.
     * @param type The object type.
     * @param configurationAction The action to run to configure the object. This action runs when the object is required.
     * @param <U> The object type
     * @return A {@link Provider} that whose value will be the object, when queried.
     * @throws InvalidUserDataException If a object with the given name already exists in this project.
     * @since 4.10
     */
    <U extends T> NamedDomainObjectProvider<U> register(String name, Class<U> type, Action<? super U> configurationAction) throws InvalidUserDataException;

    /**
     * Defines a new object, which will be created when it is required. A object is 'required' when the object is located using query methods such as {@link #getByName(String)} or when {@link Provider#get()} is called on the return value of this method.
     *
     * <p>It is generally more efficient to use this method instead of {@link #create(String, Class, Action)} or {@link #create(String, Class)}, as those methods will eagerly create and configure the object, regardless of whether that object is required for the current build or not. This method, on the other hand, will defer creation until required.</p>
     *
     * @param name The name of the object.
     * @param type The object type.
     * @param <U> The object type
     * @return A {@link Provider} that whose value will be the object, when queried.
     * @throws InvalidUserDataException If a object with the given name already exists in this project.
     * @since 4.10
     */
    <U extends T> NamedDomainObjectProvider<U> register(String name, Class<U> type) throws InvalidUserDataException;
}
