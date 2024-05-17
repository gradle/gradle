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
 * Base implementation of {@link Intersection} that handles testing and intersecting arguments in either order.
 *
 * @param <L> the type of the first (left) exclude spec
 * @param <R> the type of the second (right) exclude spec
 */
@NonNullApi
public abstract class AbstractIntersection<L extends ExcludeSpec, R extends ExcludeSpec> implements Intersection<L, R> {
    private final Class<L> leftType;
    private final Class<R> rightType;

    protected AbstractIntersection(Class<L> leftType, Class<R> rightType) {
        this.leftType = leftType;
        this.rightType = rightType;
    }

    @Override
    public boolean applies(ExcludeSpec left, ExcludeSpec right) {
        return (leftType.isInstance(left) && rightType.isInstance(right))
                || (leftType.isInstance(right) && rightType.isInstance(left));
    }

    @Override
    @Nullable
    public ExcludeSpec intersect(ExcludeSpec left, ExcludeSpec right, ExcludeFactory factory) {
        if (leftType.isInstance(left) && rightType.isInstance(right)) {
            return doIntersect(leftType.cast(left), rightType.cast(right), factory);
        } else {
            return doIntersect(leftType.cast(right), rightType.cast(left), factory);
        }
    }

    /**
     * Intersects the given exclude specs.
     *
     * This method is meant to be implemented by subclasses realizing a specific intersection of types.
     *
     * @param left an exclude spec
     * @param right another exclude spec
     * @param factory the factory that can be used to create a new exclude spec
     *
     * @return the simplified exclude spec, or {@code null} if the given exclude specs cannot be simplified
     */
    @Nullable
    abstract ExcludeSpec doIntersect(L left, R right, ExcludeFactory factory);
}
