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

package org.gradle.internal.properties.annotations;

import org.gradle.api.problems.Severity;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;
import static org.gradle.api.problems.internal.DefaultProblemCategory.VALIDATION;

public abstract class AbstractTypeAnnotationHandler implements TypeAnnotationHandler {

    protected AbstractTypeAnnotationHandler(Class<? extends Annotation> annotationType) {
        this.annotationType = annotationType;
    }

    private final Class<? extends Annotation> annotationType;

    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return annotationType;
    }

    protected static void reportInvalidUseOfTypeAnnotation(
        Class<?> classWithAnnotationAttached,
        TypeValidationContext visitor,
        Class<? extends Annotation> annotationType,
        Class<?>... appliesOnlyTo
    ) {
        visitor.visitTypeProblem(problem ->
            problem.withAnnotationType(classWithAnnotationAttached)
                .label("is incorrectly annotated with @" + annotationType.getSimpleName())
                .documentedAt(Documentation.userManual("validation_problems", "invalid_use_of_cacheable_annotation"))
                .noLocation()
                .category(VALIDATION, "type", "invalid-use-of-type-annotation")
                .severity(Severity.ERROR)
                .details(String.format("This annotation only makes sense on %s types", Arrays.stream(appliesOnlyTo)
                    .map(Class::getSimpleName)
                    .collect(joining(", "))))
                .solution("Remove the annotation")
        );
    }
}
