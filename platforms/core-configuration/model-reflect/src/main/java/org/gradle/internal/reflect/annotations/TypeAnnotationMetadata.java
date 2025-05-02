/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;
import java.util.Optional;

public interface TypeAnnotationMetadata {
    /**
     * The annotations present on the type itself.
     */
    ImmutableSet<Annotation> getAnnotations();

    /**
     * Whether an annotation of the given type is present on the type itself.
     */
    boolean isAnnotationPresent(Class<? extends Annotation> annotationType);

    /**
     * Information about the type and annotations of each property of the type.
     */
    ImmutableSortedSet<PropertyAnnotationMetadata> getPropertiesAnnotationMetadata();

    /**
     * Information about the type and annotations of each method of the type (i.e. annotated methods that are not properties).
     */
    ImmutableSortedSet<FunctionAnnotationMetadata> getFunctionAnnotationMetadata();

    void visitValidationFailures(TypeValidationContext validationContext);

    /**
     * Retrieves the annotation of the given type, if present on the type itself.
     */
    <T extends Annotation> Optional<T> getAnnotation(Class<T> annotationType);
}
