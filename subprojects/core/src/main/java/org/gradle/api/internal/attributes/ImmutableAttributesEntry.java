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

package org.gradle.api.internal.attributes;

import org.gradle.api.attributes.Attribute;
import org.gradle.internal.isolation.Isolatable;

import java.util.Objects;

/**
 * An entry of an {@link ImmutableAttributes} container.
 * <p>
 * This type contains both the attribute key and the value corresponding to that key.
 *
 * @param <T> the type of the attribute
 */
public interface ImmutableAttributesEntry<T> {

    /**
     * Get the entry's key.
     */
    Attribute<T> getKey();

    /**
     * Get the entry's value.
     */
    Isolatable<T> getValue();

    /**
     * Get an isolated instance of the entry's value.
     */
    default T getIsolatedValue() {
        return Objects.requireNonNull(getValue().isolate());
    }

    /**
     * Coerces this entry's value to the type of {@code otherAttribute}.
     *
     * @param otherAttribute the other attribute to attempt to coerce this attribute to.
     *
     * @throws IllegalArgumentException if this attribute is not compatible with the other one.
     */
    <S> S coerce(Attribute<S> otherAttribute);

}
