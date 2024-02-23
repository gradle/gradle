/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.factories;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;

import javax.annotation.Nullable;

/**
 * Represents an attempt to simplify two exclude specs into a single exclude spec.
 *
 * @param <L> the type of the first (left) exclude spec
 * @param <R> the type of the second (right) exclude spec
 *
 * @implSpec Remember that the order of the exclude specs must not be significant.
 */
@NonNullApi
public interface Intersection<L extends ExcludeSpec, R extends ExcludeSpec> {
    /**
     * Tests if this intersection applies to 2 given exclude specs (in any order).
     *
     * An intersection should apply if the given exclude specs are of the expected generic types on this interface,
     * in either order: that is, if {@code left instanceof L && right instanceof R} or {@code left instanceof R && right instanceof L}.
     *
     * @param left an exclude spec
     * @param right another exclude spec
     * @return {@code true} if this intersection applies to the given exclude specs (in any order); {@code false} otherwise
     */
    boolean applies(ExcludeSpec left, ExcludeSpec right);

    /**
     * Simplifies 2 given exclude specs (an any order) into a single exclude spec.
     *
     * @param left an exclude spec
     * @param right another exclude spec
     * @param factory the factory that can be used to create a new exclude spec
     * @return the simplified exclude spec, or {@code null} if the given exclude specs cannot be simplified
     */
    @Nullable
    ExcludeSpec intersect(ExcludeSpec left, ExcludeSpec right, ExcludeFactory factory);
}
