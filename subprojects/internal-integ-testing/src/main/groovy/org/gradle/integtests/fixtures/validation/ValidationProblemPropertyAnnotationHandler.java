/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.integtests.fixtures.validation;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.properties.annotations.AbstractPropertyAnnotationHandler;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.annotations.AnnotationCategory;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationContext;

class ValidationProblemPropertyAnnotationHandler extends AbstractPropertyAnnotationHandler {
    public ValidationProblemPropertyAnnotationHandler() {
        super(ValidationProblem.class, Kind.OTHER, ImmutableSet.of());
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        validationContext.visitPropertyProblem(problem ->
            problem.forProperty(propertyMetadata.getPropertyName())
                .withId(ValidationProblemId.TEST_PROBLEM)
                .reportAs(annotationValue(propertyMetadata))
                .withDescription("test problem")
                .documentedAt("id", "section")
                .happensBecause("this is a test")
        );
    }

    private Severity annotationValue(PropertyMetadata propertyMetadata) {
        return propertyMetadata.getAnnotationForCategory(AnnotationCategory.TYPE)
            .map(ValidationProblem.class::cast)
            .map(ValidationProblem::value)
            .orElse(Severity.WARNING);
    }
}
