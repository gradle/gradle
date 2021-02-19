/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.internal.tasks.properties.annotations.TypeAnnotationHandler;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;

import static org.gradle.internal.reflect.validation.Severity.ERROR;

public class CacheableTransformTypeAnnotationHandler implements TypeAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return CacheableTransform.class;
    }

    @Override
    public void validateTypeMetadata(Class<?> classWithAnnotationAttached, TypeValidationContext visitor) {
        if (!TransformAction.class.isAssignableFrom(classWithAnnotationAttached)) {
            visitor.visitTypeProblem(problem ->
                problem.forType(classWithAnnotationAttached)
                    .reportAs(ERROR)
                    .withId(ValidationProblemId.INVALID_USE_OF_CACHEABLE_TRANSFORM_ANNOTATION)
                    .withDescription(() -> String.format("Using %s here is incorrect", getAnnotationType().getSimpleName()))
                    .happensBecause(() -> String.format("This annotation only makes sense on %s types", TransformAction.class.getSimpleName()))
                    .documentedAt("validation_problems", "invalid_use_of_cacheable_transform_annotation")
                    .addPossibleSolution("Remove the annotation")
            );
        }
    }

}
