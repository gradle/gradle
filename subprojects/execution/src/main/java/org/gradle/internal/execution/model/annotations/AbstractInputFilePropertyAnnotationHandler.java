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

package org.gradle.internal.execution.model.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;

import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.NORMALIZATION;

public abstract class AbstractInputFilePropertyAnnotationHandler extends AbstractInputPropertyAnnotationHandler {

    public AbstractInputFilePropertyAnnotationHandler(Class<? extends Annotation> annotationType, ImmutableSet<Class<? extends Annotation>> allowedModifiers) {
        super(annotationType, allowedModifiers);
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        validateUnsupportedInputPropertyValueTypes(propertyMetadata, validationContext, getAnnotationType());
        if (!propertyMetadata.hasAnnotationForCategory(NORMALIZATION)) {
            validationContext.visitPropertyProblem(problem -> {
                String propertyName = propertyMetadata.getPropertyName();
                problem.withId(ValidationProblemId.MISSING_NORMALIZATION_ANNOTATION)
                    .reportAs(Severity.ERROR)
                    .onlyAffectsCacheableWork()
                    .forProperty(propertyName)
                    .withDescription(() -> String.format("is annotated with @%s but missing a normalization strategy", getAnnotationType().getSimpleName()))
                    .happensBecause("If you don't declare the normalization, outputs can't be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly")
                    .addPossibleSolution("Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath")
                    .documentedAt("validation_problems", "missing_normalization_annotation");
            });
        }
    }
}
