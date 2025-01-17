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

package org.gradle.internal.reflect.annotations;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Base interface for elements that have annotation metadata, such as properties and functions.
 */
public interface HasAnnotationMetadata {
    /**
     * The method that this metadata is associated with (if relevant).
     */
    Method getMethod();

    /**
     * Whether the given annotation is present on this element.
     */
    boolean isAnnotationPresent(Class<? extends Annotation> annotationType);

    /**
     * Returns the annotation of the given type that is present on this element, if any.
     */
    <T extends Annotation> Optional<T> getAnnotation(Class<T> annotationType);

    /**
     * Returns the annotations present on this element.
     */
    ImmutableMap<AnnotationCategory, Annotation> getAnnotationsByCategory();

    /**
     * Returns the declared type of this element. For a property, this is the type of the property.
     * For a function, this is the return type of the function.
     */
    TypeToken<?> getDeclaredReturnType();
}
