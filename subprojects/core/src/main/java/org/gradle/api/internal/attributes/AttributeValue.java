/*
 * Copyright 2017 the original author or authors.
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
 * Represents an attribute entry, as found in an attribute container.
 * <p>
 * This type contains both the attribute key and the value corresponding to that key.
 * <p>
 * TODO: This type should be merged with {@link AttributeEntry}.
 *
 * @param <T> the type of the attribute
 */
public interface AttributeValue<T> {

    /**
     * Get the value of this attribute entry.
     */
    default T get() {
        return Objects.requireNonNull(getIsolatable().isolate());
    }

    /**
     * Get an isolatable representation of this attribute entry's value.
     */
    Isolatable<T> getIsolatable();

    /**
     * Coerces this entry's value to the type of the other attribute, so it can be compared
     * to a value of that other attribute.
     *
     * @param otherAttribute the other attribute to attempt to coerce this attribute to
     *
     * @throws IllegalArgumentException if this attribute is not compatible with the other one
     */
    <S> S coerce(Attribute<S> otherAttribute);

    /**
     * Get the key of this attribute entry.
     */
    Attribute<T> getAttribute();

}
