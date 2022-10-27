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
package org.gradle.api.internal.tasks.properties.annotations;

import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.tasks.properties.BeanPropertyContext;
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.ServiceReference;
import org.gradle.api.tasks.Optional;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.AnnotationCategory;
import org.gradle.internal.reflect.PropertyMetadata;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.model.internal.type.ModelType;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.OPTIONAL;
import static org.gradle.internal.reflect.validation.Severity.ERROR;

public class ServiceReferencePropertyAnnotationHandler implements PropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return ServiceReference.class;
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
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor, BeanPropertyContext context) {
        if (propertyMetadata.isAnnotationPresent(ServiceReference.class)) {
            ServiceReference annotation = (ServiceReference) propertyMetadata.getAnnotationForCategory(AnnotationCategory.TYPE);
            String serviceName = StringUtils.trimToNull(annotation.value());
            visitor.visitServiceReference(propertyName, propertyMetadata.isAnnotationPresent(Optional.class), (Provider<BuildService<?>>) value.call(), serviceName);
        }
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        Method getter = propertyMetadata.getGetterMethod();
        ModelType propertyType = ModelType.returnType(getter);
        List<ModelType<?>> typeVariables = Cast.uncheckedNonnullCast(propertyType.getTypeVariables());
        if (typeVariables.size() != 1 || !BuildService.class.isAssignableFrom(typeVariables.get(0).getRawClass())) {
            validationContext.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.SERVICE_REFERENCE_MUST_BE_A_BUILD_SERVICE)
                    .forProperty(propertyMetadata.getPropertyName())
                    .reportAs(ERROR)
                    .withDescription(() -> String.format("has @ServiceReference annotation used on property of type '%s' which is not a build service implementation", typeVariables.get(0).getName()))
                    .happensBecause(() -> String.format("A property annotated with @ServiceReference must be of a type that implements '%s'", BuildService.class.getName()))
                    .addPossibleSolution(String.format("Make '%s' implement '%s'", typeVariables.get(0).getName(), BuildService.class.getName()))
                    .addPossibleSolution(String.format("Replace the @ServiceReference annotation on '%s' with @Internal and assign a value of type '%s' explicitly", propertyMetadata.getPropertyName(), typeVariables.get(0).getName()))
                    .documentedAt("validation_problems", "service_reference_must_be_a_build_service")
            );
            return;
        }
    }
}
