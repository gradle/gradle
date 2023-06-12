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

/**
 * Defines the axes of a {@link MatrixContainer}.
 */
public interface MatrixAxes {
    /**
     * Adds the given values to the given axis, registering it if necessary.
     *
     * @param axis the axis
     * @param values the values
     * @param <T> the type of values
     */
    <T> void axis(Class<? extends MatrixAxis<T>> axis, T... values);

    /**
     * Adds the given values to the given axis, registering it if necessary.
     *
     * @param axis the axis
     * @param values the values
     * @param <T> the type of values
     */
    <T> void axis(Class<? extends MatrixAxis<T>> axis, Iterable<T> values);

    // Intentionally not including getters. I'm not sure if there's a good use for them,
    // since it'd be better to inspect the finalized state via MatrixContainer...
}
