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

/**
 * Represents a property whose type is a collection of elements of type {@link T}.
 *
 * <p><b>Note:</b> This interface is not intended for implementation by build script or plugin authors. You can use the factory methods on {@link org.gradle.api.model.ObjectFactory} to create instances of this interface.
 *
 * @param <T> the type of elements.
 * @since 4.5
 */
@Incubating
public interface HasMultipleValues<T> {
    /**
     * Adds an element to the property value.
     *
     * @param element The element
     * @throws NullPointerException if the specified element is null
     */
    void add(T element);

    /**
     * Adds an element to the property value.
     *
     * <p>The given provider will be queried when the value of the property is queried.
     * The property will have no value when the given provider has no value.
     *
     * @param provider Provider
     */
    void add(Provider<? extends T> provider);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given provider will be queried when the value of the property is queried.
     * The property will have no value when the given provider has no value.
     *
     * @param provider Provider of elements
     */
    void addAll(Provider<? extends Iterable<T>> provider);
}
