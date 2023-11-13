/*
 * Copyright 2023 the original author or authors.
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
import org.gradle.api.specs.Spec;

/**
 * Allows configuring collection-based properties, namely {@link ListProperty} and {@link SetProperty}.
 *
 * @param <T>
 *
 * @since 8.6
 */
@Incubating
public interface CollectionPropertyConfigurer<T> {
    /**
     * Adds an element to the property value.
     *
     * @param element The element
     */
    void add(T element);

    /**
     * Adds an element to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * This property will have no value when the given provider has no value.
     *
     * @param provider The provider of an element
     */
    void add(Provider<? extends T> provider);

    /**
     * Adds zero or more elements to the property value.
     *
     * @param elements The elements to add
     * @since 4.10
     */
    @SuppressWarnings("unchecked")
    void addAll(T... elements);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given iterable will be queried when the value of this property is queried.
     *
     * @param elements The elements to add.
     * @since 4.10
     */
    void addAll(Iterable<? extends T> elements);

    /**
     * Adds zero or more elements to the property value.
     *
     * <p>The given provider will be queried when the value of this property is queried.
     * This property will have no value when the given provider has no value.
     *
     * @param provider Provider of elements
     */
    void addAll(Provider<? extends Iterable<? extends T>> provider);

    /**
     * Removes the given element, if found.
     *
     * @param element element to remove
     */
    void remove(T element);

    /**
     * Removes the element provided by the given provider, if found.
     *
     * @param provider a provider for an element to be be removed
     */
    void remove(Provider<T> provider);

    /**
     * Removes all elements that are matched by the given filter.
     *
     * @param filter a filter for elements to be removed
     */
    void removeIf(Spec<T> filter);

    /**
     * Removes the elements given, if found.
     *
     * @param elements elements to be removed
     */
    @SuppressWarnings("unchecked")
    void removeAll(T... elements);

    /**
     * Removes the elements given, if found.
     *
     * @param elements a collection of elements to be removed
     */
    void removeAll(Iterable<? extends T> elements);

    /**
     * Removes the elements given, if found.
     *
     * @param provider a provider for a collection of elements to be removed
     */
    void removeAll(Provider<? extends Iterable<? extends T>> provider);
}
