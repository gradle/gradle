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

package org.gradle.testing.base;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;

import java.util.Set;

/**
 * A container based on a rich identity object instead of a string name. Dimensions are defined by a
 * {@link IdentityDimensions} instance, which is usually available adjacent to the container.
 *
 * <p>
 * Unlike a {@link org.gradle.api.DomainObjectCollection} and its subtypes, identity containers are
 * <strong>always</strong> lazily evaluated.
 * </p>
 *
 * @param <V> the type of values in this container
 * @since 8.5
 */
@Incubating
public interface IdentityContainer<V extends IdentityContainer.Value> {
    /**
     * The values of an identity container. These are mutable objects with immutable identity.
     *
     * @since 8.5
     */
    @Incubating
    interface Value {
        /**
         * Returns the identity of this value.
         */
        Identity getIdentity();
    }

    /**
     * Get the property for all identities in the container. Usually this is configured with a convention to produce
     * identities from a neighboring {@link IdentityDimensions} instance.
     *
     * @return the property for all identities in the container
     */
    SetProperty<Identity> getIdentities();

    /**
     * Get a provider for all the values in the container, which are created based on the
     * {@linkplain #getIdentities() identities}.
     *
     * @return a provider for all the values in the container
     */
    Provider<Set<V>> getValues();

    /**
     * Configure all values.
     *
     * @param action the action to perform on all values
     */
    void all(Action<? super V> action);

    /**
     * Configure the values whose coordinates match the spec.
     *
     * @param spec the spec to match
     * @param action the action to perform on the matching values
     */
    void matching(Spec<? super Identity> spec, Action<? super V> action);
}
