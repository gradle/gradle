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
package org.gradle.api.internal.tasks.properties.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.internal.reflect.ParameterValidationContext;
import org.gradle.internal.reflect.PropertyMetadata;

import java.io.File;
import java.lang.annotation.Annotation;

import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.OPTIONAL;

public class InputPropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return Input.class;
    }

    @Override
    public ImmutableSet<? extends AnnotationCategory> getAllowedModifiers() {
        return ImmutableSet.of(OPTIONAL);
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public boolean shouldVisit(PropertyVisitor visitor) {
        return !visitor.visitOutputFilePropertiesOnly();
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor, BeanPropertyContext context) {
        visitor.visitInputProperty(propertyName, value, propertyMetadata.isAnnotationPresent(Optional.class));
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, ParameterValidationContext visitor) {
        Class<?> valueType = propertyMetadata.getGetterMethod().getReturnType();
        if (File.class.isAssignableFrom(valueType)
            || java.nio.file.Path.class.isAssignableFrom(valueType)
            || FileCollection.class.isAssignableFrom(valueType)) {
            visitor.visitError(null, propertyMetadata.getPropertyName(),
                String.format("has @Input annotation used on property of type %s", valueType.getName()));
        }
        if (valueType.isPrimitive() && propertyMetadata.isAnnotationPresent(Optional.class)) {
            visitor.visitError(null, propertyMetadata.getPropertyName(),
                String.format("@Input properties with primitive type '%s' cannot be @Optional", valueType.getName()));
        }
    }
}
