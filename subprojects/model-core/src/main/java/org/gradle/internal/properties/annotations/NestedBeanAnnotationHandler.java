/*
 * Copyright 2018 the original author or authors.
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
import com.google.common.reflect.TypeToken;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.properties.PropertyValue;
import org.gradle.internal.properties.PropertyVisitor;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.internal.reflect.validation.Severity.WARNING;

public class NestedBeanAnnotationHandler extends AbstractPropertyAnnotationHandler {
    public NestedBeanAnnotationHandler(Collection<Class<? extends Annotation>> allowedModifiers) {
        super(Nested.class, Kind.OTHER, ImmutableSet.copyOf(allowedModifiers));
    }

    @Override
    public boolean isPropertyRelevant() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        if (Map.class.isAssignableFrom(propertyMetadata.getDeclaredType().getRawType())) {
            Class<?> keyType = JavaReflectionUtil.extractNestedType((TypeToken<Map<?, ?>>) propertyMetadata.getDeclaredType(), Map.class, 0).getRawType();
            validateKeyType(propertyMetadata, validationContext, keyType);
        }
    }

    @Override
    public void visitPropertyValue(String propertyName, PropertyValue value, PropertyMetadata propertyMetadata, PropertyVisitor visitor) {
    }

    private static final ImmutableSet<Class<?>> SUPPORTED_KEY_TYPES = ImmutableSet.of(Enum.class, Integer.class, String.class);

    private static String getSupportedKeyTypes() {
        return SUPPORTED_KEY_TYPES.stream().map(cls -> "'" + cls.getSimpleName() + "'").collect(Collectors.joining(", "));
    }

    private static void validateKeyType(PropertyMetadata propertyMetadata, TypeValidationContext validationContext, Class<?> keyType) {
        if (!SUPPORTED_KEY_TYPES.contains(keyType)) {
            validationContext.visitPropertyProblem(problem ->
                problem.withId(ValidationProblemId.NESTED_MAP_UNSUPPORTED_KEY_TYPE)
                    .reportAs(WARNING)
                    .forProperty(propertyMetadata.getPropertyName())
                    .withDescription(() -> "where key of nested map is of type '" + keyType.getName() + "'")
                    .happensBecause("Key of nested map must be one of the following types: " + getSupportedKeyTypes())
                    .addPossibleSolution("Change type of key to one of the following types: " + getSupportedKeyTypes())
                    .documentedAt("validation_problems", "unsupported_key_type_of_nested_map")
            );
        }
    }
}
