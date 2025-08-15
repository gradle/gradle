/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.inspection;

import org.jspecify.annotations.Nullable;

/**
 * An inspection object for a given interface type that can inspect implementations for the parameters of that type.
 *
 * For instance, a TypeParameterInspection for a type {@code Foo} could inspect an implementation type {@code Bar extends Foo<T>} for the type parameter {@code T}.
 */
public interface TypeParameterInspection<INTERFACE, PARAMS> {
    /**
     * Determines the parameters type for the given implementation.
     *
     * @return The parameters type, or {@code null} when the implementation takes no parameters.
     */
    @Nullable
    <T extends INTERFACE, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType);

    /**
     * Determines the parameters type found at the given type argument index for the given implementation.
     *
     * @return The parameters type, or {@code null} when the implementation takes no parameters.
     */
    @Nullable
    public <T extends INTERFACE, P extends PARAMS> Class<P> parameterTypeFor(Class<T> implementationType, int typeArgumentIndex);
}
