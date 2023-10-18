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
import org.gradle.api.specs.Spec;

/**
 * A container with arbitrary dimensions. Dimensions are defined by a {@link MatrixDimensions} instance, which is usually available adjacent to the container.
 *
 * <p>
 * Unlike a {@link org.gradle.api.DomainObjectCollection} and its subtypes, matrix containers are <strong>always lazily evaluated</strong>. Even further, there is no way
 * to eagerly evaluate a specific item. This is because the items in a matrix container is not known until all dimensions have been configured.
 * </p>
 *
 * @param <V> the type of values in this container
 * @since 8.5
 */
@Incubating
public interface MatrixContainer<V extends MatrixContainer.MatrixValue> {
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
    void matching(Spec<? super MatrixCoordinates> spec, Action<? super V> action);

    /**
     * Configure the values whose coordinates match the spec.
     *
     * <p>Before the task graph is assembled, if spec does not match anything, an error will be thrown.</p>
     *
     * @param spec the spec to match
     * @param action the action to perform on the matching values
     */
    void require(Spec<? super MatrixCoordinates> spec, Action<? super V> action);
}
