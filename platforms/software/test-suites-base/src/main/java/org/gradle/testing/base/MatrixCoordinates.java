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
 * The coordinates of a value in a matrix.
 *
 * @since 8.5
 */
@Incubating
@HasInternalProtocol
public interface MatrixCoordinates {
    /**
     * Returns the keys for the dimensions of the matrix.
     *
     * @return the keys
     */
    Set<String> keys();

    /**
     * Returns the value for the given key.
     *
     * @param key the key
     * @throws IllegalArgumentException if the key is not registered
     */
    Object get(String key);

    /**
     * Returns the value for the given key, cast to the given type.
     *
     * @param key the key
     * @param type the type
     * @throws IllegalArgumentException if the key is not registered
     */
    default <T> T get(String key, Class<T> type) {
        return type.cast(get(key));
    }
}
