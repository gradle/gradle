/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.provider;

import org.gradle.api.Incubating;

import java.util.List;

/**
 * Represents a property whose type is a {@link List} of elements of type {@link T}.
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors. An instance of this class can be created through the factory method {@link org.gradle.api.model.ObjectFactory#listProperty(Class)}.
 *
 * @param <T> the type of elements.
 * @since 4.3
 */
@Incubating
public interface ListProperty<T> extends Property<List<T>> {
    /**
     * Adds an element to the property without evaluating existing providers already present.
     *
     * @param element The element
     * @throws NullPointerException if the specified element is null
     * @since 4.4
     */
    void add(T element);

    /**
     * Adds an element to the property given by the provider without evaluating existing providers already present.
     * <p>
     *     The given provider will be queried when it's time to get the value of the property.
     *     This property will be unchanged if its value is not defined.
     * </p>
     *
     * @param provider Provider
     * @since 4.4
     */
    void add(Provider<? extends T> provider);

    /**
     * Adds a collection of elements to the property given by the provider without evaluating existing providers
     * <p>
     *     The given provider will be queried when it's time to get the value of the property.
     *     This property will be unchanged if its value is not defined.
     * </p>
     *
     * @param provider Provider of elements
     * @since 4.4
     */
    void addAll(Provider<? extends Iterable<T>> provider);
}
