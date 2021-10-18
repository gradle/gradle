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
package org.gradle.api.internal.tasks.properties.annotations;

import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.gradle.internal.reflect.problems.ValidationProblemId.INVALID_USE_OF_TYPE_ANNOTATION;
import static org.gradle.internal.reflect.validation.Severity.ERROR;

public class TypeAnnotationHandlerSupport {

    public static void reportInvalidUseOfTypeAnnotation(Class<?> classWithAnnotationAttached,
                                                        TypeValidationContext visitor,
                                                        Class<? extends Annotation> annotationType,
                                                        Class<?>... appliesOnlyTo) {
        visitor.visitTypeProblem(problem ->
            problem.forType(classWithAnnotationAttached)
                .reportAs(ERROR)
                .withId(INVALID_USE_OF_TYPE_ANNOTATION)
                .withDescription(() -> "is incorrectly annotated with @" + annotationType.getSimpleName())
                .happensBecause(() -> String.format("This annotation only makes sense on %s types", Arrays.stream(appliesOnlyTo)
                    .map(Class::getSimpleName)
                    .collect(Collectors.joining(", "))
                ))
                .documentedAt("validation_problems", "invalid_use_of_cacheable_annotation")
                .addPossibleSolution("Remove the annotation")
        );
    }
}
