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

import java.util.function.Predicate;

/**
 * Allows configuring collection-based properties, namely {@link ListProperty} and {@link SetProperty}.
 *
 * @param <T>
 *
 * @since 8.5
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

    void exclude(Predicate<T> filter);

    void exclude(Provider<T> provider);

    void excludeAll(Iterable<? extends T> elements);

    void excludeAll(Provider<? extends Iterable<? extends T>> provider);
}
