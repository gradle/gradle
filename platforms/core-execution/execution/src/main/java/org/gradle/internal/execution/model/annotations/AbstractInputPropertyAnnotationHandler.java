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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.problems.ProblemBuilder;
import org.gradle.api.problems.internal.DefaultProblemCategory;
import org.gradle.api.problems.Severity;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.properties.annotations.AbstractPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.internal.deprecation.Documentation.userManual;

abstract class AbstractInputPropertyAnnotationHandler extends AbstractPropertyAnnotationHandler {
    protected AbstractInputPropertyAnnotationHandler(Class<? extends Annotation> annotationType, ImmutableSet<Class<? extends Annotation>> allowedModifiers) {
        super(annotationType, Kind.INPUT, allowedModifiers);
    }

    protected static void validateUnsupportedInputPropertyValueTypes(
        PropertyMetadata propertyMetadata,
        TypeValidationContext validationContext,
        Class<? extends Annotation> annotationType
    ) {
        validateUnsupportedPropertyValueType(
            annotationType, unpackValueTypesOf(propertyMetadata),
            propertyMetadata, validationContext,
            ResolvedArtifactResult.class,
            "Extract artifact metadata and annotate with @Input",
            "Extract artifact files and annotate with @InputFiles"
        );
    }

    private static final String UNSUPPORTED_VALUE_TYPE = "UNSUPPORTED_VALUE_TYPE";

    private static void validateUnsupportedPropertyValueType(
        Class<? extends Annotation> annotationType,
        List<Class<?>> valueTypes,
        PropertyMetadata propertyMetadata,
        TypeValidationContext validationContext,
        Class<?> unsupportedType,
        String... possibleSolutions
    ) {
        if (valueTypes.stream().anyMatch(unsupportedType::isAssignableFrom)) {
            validationContext.visitPropertyProblem(problem -> {
                    ProblemBuilder describedProblem = problem
                        .forProperty(propertyMetadata.getPropertyName())
                        .label("has @%s annotation used on property of type '%s'", annotationType.getSimpleName(), TypeOf.typeOf(propertyMetadata.getDeclaredType().getType()).getSimpleName())
                        .documentedAt(userManual("validation_problems", UNSUPPORTED_VALUE_TYPE.toLowerCase()))
                        .noLocation()
                        .category(DefaultProblemCategory.VALIDATION, UNSUPPORTED_VALUE_TYPE)
                        .severity(Severity.ERROR)
                        .details(String.format("%s is not supported on task properties annotated with @%s", unsupportedType.getSimpleName(), annotationType.getSimpleName()));
                    for (String possibleSolution : possibleSolutions) {
                        describedProblem.solution(possibleSolution);
                    }
                }
            );
        }
    }

    protected static List<Class<?>> unpackValueTypesOf(PropertyMetadata propertyMetadata) {
        List<Class<?>> unpackedValueTypes = new ArrayList<>();
        Class<?> returnType = propertyMetadata.getDeclaredType().getRawType();
        if (Provider.class.isAssignableFrom(returnType)) {
            List<TypeOf<?>> typeArguments = TypeOf.typeOf(propertyMetadata.getDeclaredType().getType()).getActualTypeArguments();
            for (TypeOf<?> typeArgument : typeArguments) {
                unpackedValueTypes.add(typeArgument.getConcreteClass());
            }
        } else {
            unpackedValueTypes.add(returnType);
        }
        return unpackedValueTypes;
    }
}
