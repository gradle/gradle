/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.registration;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollection;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.provider.Provider;

/**
 * An interface for operations relating to registering new named domain objects in a collection.
 *
 * @param <T> The type of objects which this registrar creates.
 * @since 9.5.0
 */
@Incubating
public interface NamedDomainObjectRegistrar<T> {
    /**
     * Defines a new object, which will be created and configured when it is required. An object is 'required' when the object is located using query methods such as {@link NamedDomainObjectCollection#getByName(String)} or when {@link Provider#get()} is called on the return value of this method.
     * <p>
     * It is generally more efficient to use this method instead of {@link NamedDomainObjectContainer#create(String, Action)} or {@link NamedDomainObjectContainer#create(String)}, as those methods will eagerly create and configure the object, regardless of whether that object is required for the current build or not.
     * This method, on the other hand, will defer creation and configuration until required.
     * <p>
     * This operation is lazy, the returned element is NOT realized.
     * A {@link NamedDomainObjectProvider lazy wrapper} is returned, allowing to continue to use it with other lazy APIs.
     *
     * @param name The name of the object.
     * @param configurationAction The action to run to configure the object. This action runs when the object is required.
     * @return A {@link Provider} whose value will be the object, when queried.
     * @throws InvalidUserDataException If a object with the given name already exists in this project.
     * @since 9.5.0
     */
    NamedDomainObjectProvider<T> register(String name, Action<? super T> configurationAction) throws InvalidUserDataException;

    /**
     * Defines a new object, which will be created when it is required. A object is 'required' when the object is located using query methods such as {@link NamedDomainObjectCollection#getByName(String)} or when {@link Provider#get()} is called on the return value of this method.
     * <p>
     * It is generally more efficient to use this method instead of {@link NamedDomainObjectContainer#create(String)}, as that method will eagerly create the object, regardless of whether that object is required for the current build or not.
     * This method, on the other hand, will defer creation until required.
     * <p>
     * This operation is lazy, the returned element is NOT realized.
     * A {@link NamedDomainObjectProvider lazy wrapper} is returned, allowing to continue to use it with other lazy APIs.
     *
     * @param name The name of the object.
     * @return A {@link Provider} whose value will be the object, when queried.
     * @throws InvalidUserDataException If a object with the given name already exists in this project.
     * @since 9.5.0
     */
    NamedDomainObjectProvider<T> register(String name) throws InvalidUserDataException;
}
