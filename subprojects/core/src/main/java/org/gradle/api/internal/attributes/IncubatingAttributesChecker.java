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

package org.gradle.api.internal.attributes;

import org.gradle.api.Incubating;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.internal.Pair;

import javax.annotation.Nullable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Static util class which provides utilities to check if an {@link Attribute} is {@link org.gradle.api.Incubating}.
 *
 * An attribute is incubating iff it is a type annotated with {@link org.gradle.api.Incubating}, or if the value is equal to the value of a constant defined
 * in the corresponding attribute interface which is annotated with {@link org.gradle.api.Incubating}.
 *
 * For example, any attribute named {@link org.gradle.api.attributes.TestSuiteType} is currently incubating, as is a {@link Category#CATEGORY_ATTRIBUTE} with a value
 * equal to {@link Category#VERIFICATION}.
 *
 * @since 7.5
 */
public abstract class IncubatingAttributesChecker {
    public static <T> boolean isIncubating(Attribute<T> attribute) {
        return isIncubatingAttributeValue(attribute.getType(), null);
    }

    public static <T> boolean isIncubating(Attribute<T> key, @Nullable T value) {
        if (isIncubatingAttributeInterface(key)) {
            return true;
        } else {
            return isIncubatingAttributeValue(key.getType(), value);
        }
    }

    @SuppressWarnings("unchecked")
    public static boolean isAnyIncubating(AttributeContainer attributes) {
        return attributes.keySet().stream().map(k -> Pair.of((Attribute<Object>) k, (Object) attributes.getAttribute(k))).anyMatch(IncubatingAttributesChecker::isIncubating);
    }

    private static <T> boolean isIncubating(Pair<Attribute<T>, T> attributePair) {
        return isIncubating(Objects.requireNonNull(attributePair.getLeft()), attributePair.getRight());
    }

    private static <T> boolean isIncubatingAttributeInterface(Attribute<T> key) {
        return isAnnotatedWithIncubating(key.getType());
    }

    private static <T> boolean isIncubatingAttributeValue(Class<T> type, @Nullable T value) {
        String valueStr = value != null ? value.toString() : null;
        return getIncubatingFields(type).stream().map(IncubatingAttributesChecker::getFieldValue).anyMatch(Predicate.isEqual(valueStr));
    }

    private static Object getFieldValue(Field f) {
        try {
            return f.get(null);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error reading field: " + f.getName() + " on type: " + f.getType().getName(), e);
        }
    }

    private static List<Field> getIncubatingFields(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields()).filter(IncubatingAttributesChecker::isAnnotatedWithIncubating).collect(Collectors.toList());
    }

    private static boolean isAnnotatedWithIncubating(AnnotatedElement element) {
        return element.isAnnotationPresent(Incubating.class);
    }
}
