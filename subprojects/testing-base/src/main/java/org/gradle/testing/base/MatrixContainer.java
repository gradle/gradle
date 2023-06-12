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
import org.gradle.internal.HasInternalProtocol;

/**
 * A container with arbitrary dimensions. Axes are defined by a {@link MatrixAxes} instance, which is usually available adjacent to the container.
 *
 * @param <S> the spec type, used to select coordinates for operations
 * @param <V> the value type
 */
public interface MatrixContainer<S extends MatrixContainer.MatrixSpec, V extends MatrixContainer.MatrixValue> {
    @HasInternalProtocol
    interface MatrixSpec {
        /**
         * Set the required values for the given axis.
         *
         * @param axis the axis
         * @param values the values
         * @throws IllegalArgumentException if the axis is not registered
         * @throws IllegalArgumentException if the values are empty
         */
        <T> void axis(Class<? extends MatrixAxis<T>> axis, T... values);

        /**
         * Set the required values for the given axis.
         *
         * @param axis the axis
         * @param values the values
         * @throws IllegalArgumentException if the axis is not registered
         * @throws IllegalArgumentException if the values are empty
         */
        <T> void axis(Class<? extends MatrixAxis<T>> axis, Iterable<T> values);
    }

    /**
     * The values of a matrix. These are mutable objects with immutable coordinates.
     */
    interface MatrixValue {
        /**
         * Returns the coordinates of this value.
         */
        MatrixCoordinates getCoordinates();
    }

    /**
     * The coordinates of a value in a matrix.
     */
    @HasInternalProtocol
    interface MatrixCoordinates {
        /**
         * Returns the value for the given axis.
         *
         * @param axis the axis
         * @throws IllegalArgumentException if the axis is not registered
         */
        <T> T get(Class<? extends MatrixAxis<T>> axis);
    }

    /**
     * Configure all values.
     *
     * @param action the action to perform on all values
     */
    void all(Action<? super V> action);

    /**
     * Configure the values whose coordinates match the spec.
     *
     * @param spec the spec configuration
     * @param action the action to perform on the matching values
     */
    void with(Action<? super S> spec, Action<? super V> action);
}
