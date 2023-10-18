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
import org.gradle.api.specs.Spec;

/**
 * Defines the dimensions of a {@link MatrixContainer}.
 *
 * <a id="including-and-excluding"></a>
 * <h2>Including and Excluding</h2>
 * <p>
 * The coordinates of a matrix is the product of each dimension's values. A coordinate is included if and only if:
 * <ul>
 * <li>It matches all of the included specs, or there are no included specs</li>
 * <li>It does not match any of the excluded specs</li>
 * </ul>
 * <p>
 * These are the same rules as {@link org.gradle.api.tasks.util.PatternFilterable}.
 * </p>
 *
 * @since 8.5
 */
@Incubating
public interface MatrixDimensions {
    /**
     * Includes coordinates that match the given spec.
     *
     * @param spec the spec
     * @see <a href="#including-and-excluding">Including and Excluding</a>
     */
    void include(Spec<? super MatrixCoordinates> spec);

    /**
     * Excludes coordinates that match the given spec.
     *
     * @param spec the spec
     * @see <a href="#including-and-excluding">Including and Excluding</a>
     */
    void exclude(Spec<? super MatrixCoordinates> spec);

    /**
     * Adds the given values to the given dimension, registering it if necessary.
     *
     * @param dimension the dimension
     * @param values the values
     * @param <T> the type of values
     */
    <T> void dimension(String dimension, Iterable<T> values);

    // Intentionally not including getters. I'm not sure if there's a good use for them,
    // since it'd be better to inspect the finalized state via MatrixContainer...
}
