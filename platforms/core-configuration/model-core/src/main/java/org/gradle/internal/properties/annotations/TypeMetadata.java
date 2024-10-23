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

package org.gradle.internal.properties.annotations;

import org.gradle.internal.reflect.annotations.TypeAnnotationMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import javax.annotation.Nullable;
import java.util.Set;

public interface TypeMetadata {
    void visitValidationFailures(@Nullable String ownerPropertyPath, TypeValidationContext validationContext);

    /**
     * Returns the set of relevant properties, that is, those properties annotated with a relevant annotation.
     */
    Set<PropertyMetadata> getPropertiesMetadata();

    boolean hasAnnotatedProperties();

    PropertyAnnotationHandler getAnnotationHandlerFor(PropertyMetadata propertyMetadata);

    TypeAnnotationMetadata getTypeAnnotationMetadata();

    /**
     * Returns the type this {@link TypeMetadata} belongs to.
     *
     * @return the type
     */
    Class<?> getType();
}
