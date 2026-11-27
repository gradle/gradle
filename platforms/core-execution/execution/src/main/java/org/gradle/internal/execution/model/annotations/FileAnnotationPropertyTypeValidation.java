/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.provider.HasMultipleValues;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.gradle.internal.deprecation.Documentation.userManual;

/**
 * Reports file annotations used on a property whose declared type represents a different kind of location than the annotation expects,
 * for example {@code @InputFile} on a {@link DirectoryProperty}.
 */
public final class FileAnnotationPropertyTypeValidation {

    public enum LocationKind {
        REGULAR_FILE("a regular file") {
            @Override
            boolean matches(Class<?> type) {
                return RegularFile.class.isAssignableFrom(type) || RegularFileProperty.class.isAssignableFrom(type);
            }
        },
        DIRECTORY("a directory") {
            @Override
            boolean matches(Class<?> type) {
                return Directory.class.isAssignableFrom(type) || DirectoryProperty.class.isAssignableFrom(type);
            }
        },
        FILE_COLLECTION("a collection of files") {
            @Override
            boolean matches(Class<?> type) {
                return isCollection(type);
            }
        },
        /**
         * Excludes types that can hold a {@link ConfigurableFileTree}, which {@code @InputDirectory} accepts as its root directory.
         */
        FILE_COLLECTION_OTHER_THAN_FILE_TREE("a collection of files") {
            @Override
            boolean matches(Class<?> type) {
                return isCollection(type) && !type.isAssignableFrom(ConfigurableFileTree.class);
            }
        };

        private final String description;

        LocationKind(String description) {
            this.description = description;
        }

        abstract boolean matches(Class<?> type);
    }

    private FileAnnotationPropertyTypeValidation() {
    }

    /**
     * Reports a problem if the declared type of the property, or the value type of a {@code Provider} property, is of the given {@code unsupportedKind}.
     */
    public static void validateNotOfKind(
        Class<? extends Annotation> annotationType,
        String expectedKind,
        LocationKind unsupportedKind,
        PropertyMetadata propertyMetadata,
        TypeValidationContext validationContext,
        String... solutions
    ) {
        if (declaredAndValueTypesOf(propertyMetadata).stream().noneMatch(unsupportedKind::matches)) {
            return;
        }
        String annotationName = annotationType.getSimpleName();
        String propertyType = TypeOf.typeOf(propertyMetadata.getDeclaredType().getType()).getSimpleName();
        validationContext.visitPropertyError(problem -> {
            ProblemSpec describedProblem = problem
                .forProperty(propertyMetadata.getPropertyName())
                .id(problem.getGradleGroup().getPluginValidation().problemId("Incorrect use of @" + annotationName + " annotation"))
                .contextualLabel(String.format("has @%s annotation used on property of type '%s'", annotationName, propertyType))
                .documentedAt(userManual("validation_problems", "incorrect_use_of_file_annotation"))
                .details(String.format("A property of type '%s' represents %s, but @%s expects %s", propertyType, unsupportedKind.description, annotationName, expectedKind));
            for (String solution : solutions) {
                describedProblem.solution(solution);
            }
        });
    }

    private static boolean isCollection(Class<?> type) {
        return (Iterable.class.isAssignableFrom(type) && !Path.class.isAssignableFrom(type))
            || Map.class.isAssignableFrom(type)
            || HasMultipleValues.class.isAssignableFrom(type)
            || MapProperty.class.isAssignableFrom(type)
            || type.isArray();
    }

    private static List<Class<?>> declaredAndValueTypesOf(PropertyMetadata propertyMetadata) {
        List<Class<?>> types = new ArrayList<>();
        types.add(propertyMetadata.getDeclaredType().getRawType());
        types.addAll(AbstractInputPropertyAnnotationHandler.unpackValueTypesOf(propertyMetadata));
        return types;
    }
}
