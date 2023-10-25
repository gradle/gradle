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

import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

import java.util.Set;

/**
 * The identity of a value in an {@link IdentityContainer}.
 *
 * @since 8.5
 */
@Incubating
@HasInternalProtocol
public interface Identity {
    /**
     * Returns the property names of the identity.
     *
     * @return the names
     */
    Set<String> getPropertyNames();

    /**
     * Returns the value for the given property name.
     *
     * @param propertyName the property name
     * @throws IllegalArgumentException if the property name is not known
     */
    Object get(String propertyName);

    /**
     * Returns the value for the given property name, cast to the given type.
     *
     * @param propertyName the property name
     * @param type the type
     * @throws IllegalArgumentException if the property name is not known
     */
    default <T> T get(String propertyName, Class<T> type) {
        return type.cast(get(propertyName));
    }
}
