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

package org.gradle.plugin.software.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.plugins.software.SoftwareType;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.AbstractPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;

public class SoftwareTypeAnnotationHandler extends AbstractPropertyAnnotationHandler {

    public SoftwareTypeAnnotationHandler() {
        super(SoftwareType.class, Kind.OTHER, ImmutableSet.of());
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
        propertyMetadata.getAnnotation(SoftwareType.class).ifPresent(softwareType -> {
            visitor.visitSoftwareTypeProperty(propertyName, value, softwareType);
        });
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
//        propertyMetadata.getAnnotation(SoftwareType.class).ifPresent(softwareType -> {
//            Class<?> publicType = softwareType.modelPublicType();
//            if (!publicType.isAssignableFrom(propertyMetadata.getDeclaredType().getRawType())) {
//                validationContext.addError("Property '%s' is annotated with @SoftwareType, but the property type is not a subtype of the declared return type '%s'", propertyMetadata.getPropertyName(), publicType.getName());
//            }
//        });
    }
}
