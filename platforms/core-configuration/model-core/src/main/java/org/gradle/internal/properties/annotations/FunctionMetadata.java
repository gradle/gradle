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

package org.gradle.internal.properties.annotations;

import com.google.common.reflect.TypeToken;
import org.gradle.internal.reflect.annotations.AnnotationCategory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Represents the metadata of a function (i.e. an annotated method not associated with a property).
 */
public interface FunctionMetadata {
    String getMethodName();

    Method getMethod();

    boolean isAnnotationPresent(Class<? extends Annotation> annotationType);

    <T extends Annotation> Optional<T> getAnnotation(Class<T> annotationType);

    Optional<Annotation> getAnnotationForCategory(AnnotationCategory category);

    boolean hasAnnotationForCategory(AnnotationCategory category);

    Class<? extends Annotation> getFunctionType();

    TypeToken<?> getDeclaredType();
}
