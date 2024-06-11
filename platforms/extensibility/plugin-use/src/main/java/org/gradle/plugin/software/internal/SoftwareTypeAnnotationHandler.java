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
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.AbstractPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.model.internal.type.ModelType;

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
        propertyMetadata.getAnnotation(SoftwareType.class).ifPresent(softwareType ->
            visitor.visitSoftwareTypeProperty(propertyName, value, softwareType)
        );
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        propertyMetadata.getAnnotation(SoftwareType.class).ifPresent(softwareType -> {
            Class<?> publicType = softwareType.modelPublicType();
            Class<?> valueType = propertyMetadata.getDeclaredType().getRawType();
            if (publicType != Void.class && !publicType.isAssignableFrom(valueType)) {
                validationContext.visitPropertyProblem(problem ->
                    problem
                        .forProperty(propertyMetadata.getPropertyName())
                        .id("mismatched-types-for-software-type", "has @SoftwareType annotation used on property", GradleCoreProblemGroup.validation().property())
                        .contextualLabel(String.format("has @SoftwareType annotation with public type '%s' used on property of type '%s'", ModelType.of(publicType).getDisplayName(), ModelType.of(valueType).getDisplayName()))
                        .severity(Severity.ERROR)
                        .details("The publicType value of the @SoftwareType annotation (" + ModelType.of(publicType).getDisplayName() + ") must be the same type as or a supertype of '" + ModelType.of(valueType).getDisplayName() + "'")
                        .solution("Provide a public type for @SoftwareType that is the same type as or a supertype of the property type '" + ModelType.of(valueType).getDisplayName() + "'")
                );
            }
        });
    }
}
