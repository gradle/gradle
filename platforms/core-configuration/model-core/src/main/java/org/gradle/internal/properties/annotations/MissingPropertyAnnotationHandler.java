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

package org.gradle.internal.properties.annotations;

import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.internal.reflect.annotations.PropertyAnnotationMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.TextUtil;

import static org.gradle.api.problems.Severity.ERROR;
import static org.gradle.internal.deprecation.Documentation.userManual;

/**
 * A handler for properties discovered without a valid annotation.
 */
public interface MissingPropertyAnnotationHandler {
    void handleMissingPropertyAnnotation(TypeValidationContext context, PropertyAnnotationMetadata annotationMetadata, String displayName);

    MissingPropertyAnnotationHandler DO_NOTHING = (context, annotationMetadata, displayName) -> {};


    MissingPropertyAnnotationHandler MISSING_INPUT_OUTPUT_HANDLER = (context, annotationMetadata, displayName) -> context.visitPropertyProblem(problem -> {
        final String missingAnnotation = "MISSING_ANNOTATION";
        problem
            .forProperty(annotationMetadata.getPropertyName())
            .id(TextUtil.screamingSnakeToKebabCase(missingAnnotation), "Missing annotation", GradleCoreProblemGroup.validation().property())
            .contextualLabel("is missing " + displayName)
            .documentedAt(userManual("validation_problems", TextUtil.toLowerCaseLocaleSafe(missingAnnotation)))
            .severity(ERROR)
            .details("A property without annotation isn't considered during up-to-date checking")
            .solution("Add " + displayName)
            .solution("Mark it as @Internal");
    });
}
