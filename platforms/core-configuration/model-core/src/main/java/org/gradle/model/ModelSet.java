/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

import java.util.Set;

/**
 * A set of managed model objects.
 * <p>
 * {@link org.gradle.model.Managed} types may declare managed set properties.
 * Managed sets can only contain managed types.
 * <p>
 * Managed set objects cannot be mutated via the mutative methods of the {@link java.util.Set} interface (e.g. {@link java.util.Set#add(Object)}, {@link java.util.Set#clear()}).
 * To add elements to the set, the {@link #create(Action)} method can be used.
 *
 * @param <T> the type of model object
 */
@Incubating
public interface ModelSet<T> extends Set<T>, ModelElement {

    /**
     * Declares a new set element, configured by the given action.
     *
     * @param action the object configuration
     */
    void create(Action<? super T> action);

    /**
     * Apply the given action to each set element just after it is created.
     * <p>
     * The configuration action is equivalent in terms of lifecycle to {@link org.gradle.model.Defaults} rule methods.
     *
     * @param configAction the object configuration
     */
    void beforeEach(Action<? super T> configAction);

    /**
     * Apply the given action to each set element just before it is considered to be realised.
     * <p>
     * The configuration action is equivalent in terms of lifecycle to {@link org.gradle.model.Finalize} rule methods.
     *
     * @param configAction the object configuration
     */
    void afterEach(Action<? super T> configAction);
}
