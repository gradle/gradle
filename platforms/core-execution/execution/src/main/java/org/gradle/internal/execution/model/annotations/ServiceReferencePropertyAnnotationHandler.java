/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.internal.execution.model.annotations;

import com.google.common.reflect.TypeToken;
import org.gradle.api.problems.Severity;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.Cast;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.AbstractPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.ParameterizedType;
import java.util.List;

import static org.gradle.internal.deprecation.Documentation.userManual;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.OPTIONAL;

public class ServiceReferencePropertyAnnotationHandler extends AbstractPropertyAnnotationHandler {
    public ServiceReferencePropertyAnnotationHandler() {
        super(ServiceReference.class, Kind.OTHER, ModifierAnnotationCategory.annotationsOf(OPTIONAL));
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
        propertyMetadata.getAnnotation(ServiceReference.class).ifPresent(annotation -> {
            String serviceName = annotation.value();
            TypeToken<?> declaredType = propertyMetadata.getDeclaredType();
            Class<?> serviceType = Cast.uncheckedCast(((ParameterizedType) declaredType.getType()).getActualTypeArguments()[0]);
            visitor.visitServiceReference(propertyName, propertyMetadata.isAnnotationPresent(Optional.class), value, serviceName, Cast.uncheckedCast(serviceType));
        });
    }

    private static final String SERVICE_REFERENCE_MUST_BE_A_BUILD_SERVICE = "SERVICE_REFERENCE_MUST_BE_A_BUILD_SERVICE";

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        ModelType<?> propertyType = ModelType.of(propertyMetadata.getDeclaredType().getType());
        List<ModelType<?>> typeVariables = Cast.uncheckedNonnullCast(propertyType.getTypeVariables());
        if (typeVariables.size() != 1 || !BuildService.class.isAssignableFrom(typeVariables.get(0).getRawClass())) {
            validationContext.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyMetadata.getPropertyName())
                    .label(String.format("has @ServiceReference annotation used on property of type '%s' which is not a build service implementation", typeVariables.get(0).getName()))
                    .documentedAt(userManual("validation_problems", SERVICE_REFERENCE_MUST_BE_A_BUILD_SERVICE.toLowerCase()))
                    .noLocation()
                    .category(SERVICE_REFERENCE_MUST_BE_A_BUILD_SERVICE)
                    .severity(Severity.ERROR)
                    .details(String.format("A property annotated with @ServiceReference must be of a type that implements '%s'", BuildService.class.getName()))
                    .solution(String.format("Make '%s' implement '%s'", typeVariables.get(0).getName(), BuildService.class.getName()))
                    .solution(String.format("Replace the @ServiceReference annotation on '%s' with @Internal and assign a value of type '%s' explicitly", propertyMetadata.getPropertyName(), typeVariables.get(0).getName()))
            );
        }
    }
}
