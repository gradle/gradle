/*
 * Copyright 2023 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.NonNullApi;
import org.gradle.api.problems.interfaces.Severity;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.util.stream.Collectors;

import static org.gradle.api.problems.interfaces.ProblemGroup.TYPE_VALIDATION_ID;
import static org.gradle.internal.deprecation.Documentation.userManual;

/**
 * Utility methods for validating {@link org.gradle.api.tasks.Nested} properties.
 */
@NonNullApi
public class NestedValidationUtil  {
    /**
     * Validates that the {@link org.gradle.api.tasks.Nested} annotation
     * supports the given bean type.
     * <p>
     * Nested types are expected to either declare some annotated properties,
     * which themselves are checked for annotations, or some conditional
     * behaviour where capturing the type itself as input is important.
     * <p>
     * Types of the Java SE API, types of the Kotlin stdlib, and Groovy's
     * GString type are not supported because they meet neither of those
     * requirements.
     *
     * @param validationContext the validation context
     * @param propertyName the name of the property
     * @param beanType the type of the bean
     */
    public static void validateBeanType(
        TypeValidationContext validationContext,
        String propertyName,
        Class<?> beanType
    ) {
        if (!isSupportedType(beanType)) {
            validationContext.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyName)
                    .documentedAt(userManual("validation_problems", "unsupported_nested_type"))
                    .noLocation()
                    .severity(Severity.WARNING)
                    .message("with nested type '" + beanType.getName() + "' is not supported")
                    .type(ValidationProblemId.NESTED_TYPE_UNSUPPORTED.name())
                    .group(TYPE_VALIDATION_ID)
                    .description("Nested types are expected to either declare some annotated properties or some behaviour that requires capturing the type as input")
                    .solution("Declare a nested type, e.g. `Provider<T>`, `Iterable<T>`, or `<MapProperty<K, V>>`, where `T` and `V` have some annotated properties or some behaviour that requires capturing the type as input")
            );
        }
    }

    private static boolean isJavaSE(Class<?> type) {
        return type.getName().startsWith("java.") || type.getName().startsWith("javax.");
    }

    private static boolean isKotlinStdlib(Class<?> type) {
        return type.getName().startsWith("kotlin.") || type.getName().startsWith("kotlinx.");
    }

    private static boolean isGString(Class<?> type) {
        return type.getName().startsWith("groovy.lang.GString");
    }

    private static boolean isSupportedType(Class<?> type) {
        return !isJavaSE(type) && !isKotlinStdlib(type) && !isGString(type);
    }

    /**
     * Validates that the {@link org.gradle.api.tasks.Nested} annotation
     * supports the given map key type.
     * @param validationContext the validation context
     * @param propertyName the name of the property
     * @param keyType the type of the map key
     */
    public static void validateKeyType(
        TypeValidationContext validationContext,
        String propertyName,
        Class<?> keyType
    ) {
        if (!SUPPORTED_KEY_TYPES.contains(keyType)) {
            validationContext.visitPropertyProblem(problem ->
                problem
                    .forProperty(propertyName)
                    .documentedAt(userManual("validation_problems", "unsupported_key_type_of_nested_map"))
                    .noLocation()
                    .severity(Severity.WARNING)
                    .message("where key of nested map is of type '" + keyType.getName() + "'")
                    .type(ValidationProblemId.NESTED_MAP_UNSUPPORTED_KEY_TYPE.name())
                    .group(TYPE_VALIDATION_ID)
                    .description("Key of nested map must be one of the following types: " + getSupportedKeyTypes())
                    .solution("Change type of key to one of the following types: " + getSupportedKeyTypes())
            );
        }
    }

    private static final ImmutableSet<Class<?>> SUPPORTED_KEY_TYPES = ImmutableSet.of(Enum.class, Integer.class, String.class);

    private static String getSupportedKeyTypes() {
        return SUPPORTED_KEY_TYPES.stream().map(cls -> "'" + cls.getSimpleName() + "'").collect(Collectors.joining(", "));
    }
}
