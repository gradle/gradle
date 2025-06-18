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

/**
 * A type safe pair of an {@link Attribute} key and an {@link Isolatable} value which has the
 * same type as the attribute.
 *
 * @param <T> The type that the key and value share.
 */
public class AttributeEntry<T> {

    private final Attribute<T> key;
    private final Isolatable<T> value;

    public AttributeEntry(Attribute<T> key, Isolatable<T> value) {
        this.key = key;
        this.value = value;
    }

    public Attribute<T> getKey() {
        return key;
    }

    public Isolatable<T> getValue() {
        return value;
    }

}
