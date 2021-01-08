/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.collect.ImmutableCollection;

import javax.annotation.Nullable;

class ValidatingValueCollector<T> implements ValueCollector<T> {
    private final Class<?> collectionType;
    private final Class<T> elementType;
    private final ValueSanitizer<T> sanitizer;

    ValidatingValueCollector(Class<?> collectionType, Class<T> elementType, ValueSanitizer<T> sanitizer) {
        this.collectionType = collectionType;
        this.elementType = elementType;
        this.sanitizer = sanitizer;
    }

    @Override
    public void add(@Nullable T value, ImmutableCollection.Builder<T> dest) {
        T sanitized = sanitizer.sanitize(value);
        if (!elementType.isInstance(sanitized)) {
            throw new IllegalArgumentException(String.format("Cannot get the value of a property of type %s with element type %s as the source value contains an element of type %s.", collectionType.getName(), elementType.getName(), value.getClass().getName()));
        }
        dest.add(sanitized);
    }

    @Override
    public void addAll(Iterable<? extends T> values, ImmutableCollection.Builder<T> dest) {
        for (T value : values) {
            add(value, dest);
        }
    }
}
