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

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * A protocol for objects that can be incrementally updated in a lazy way.
 *
 * @param <C> the configurer type, the type that models the configuration protocol
 *
 * @since 8.6
 */
@Incubating
public interface Updatable<C extends Updatable.Configurer> {
    /**
     * A base interface for incrementally mutating configurable objects returned by
     * {@link Updatable#getActualValue()} and similar methods.
     *
     * Implementations are supposed to provide only (incrementally) mutating methods,
     * with no return values, and not querying methods.
     *
     * @since 8.6
     */
    @Incubating
    interface Configurer {
    }

    //TODO-RC remove
    /**
     * Returns the value configurer for this property's explicit value,
     * be it explicitly assigned or defined by convention.
     *
     * @since 8.6
     */
    @Incubating
    C getActualValue();

    /**
     * Configures this object by executing the given configuration action.
     *
     * @param action an action to incrementally configure this object
     *
     * @since 8.6
     */
    @Incubating
    Updatable<C> configure(Action<C> action);
}
