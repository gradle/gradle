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

package org.gradle.internal.reflect;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.reflect.PublicType;
import org.gradle.api.reflect.PublicTypeSupplier;
import org.gradle.api.reflect.TypeOf;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;

/**
 * Detect declared public type from {@link PublicType} annotations.
 */
public class PublicTypeAnnotationDetector {

    private PublicTypeAnnotationDetector() {
        // utility class, blocking the creation of instances
    }

    @Nullable
    public static TypeOf<?> detect(Class<?> targetType) {
        Class<?> type = targetType;
        while (type != null) {
            validateUsageOnClassesOnly(targetType, type);
            if (type.isAnnotationPresent(PublicType.class)) {
                PublicType annotation = type.getAnnotation(PublicType.class);
                Class<?> typeParameter = annotation.type();
                Class<? extends PublicTypeSupplier> supplierParameter = annotation.supplier();
                boolean nonDefaultType = !PublicType.UseAnnotatedType.class.equals(typeParameter);
                boolean nonDefaultSupplier = !PublicType.NoSupplier.class.equals(supplierParameter);
                if (nonDefaultType && nonDefaultSupplier) {
                    throw invalidAnnotationParameters(targetType, type);
                }
                if (nonDefaultSupplier) {
                    return newPublicTypeSupplierFor(targetType, type, supplierParameter).getPublicType();
                }
                if (nonDefaultType) {
                    return TypeOf.typeOf(typeParameter);
                }
                return TypeOf.typeOf(type);
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static void validateUsageOnClassesOnly(Class<?> targetType, Class<?> type) {
        if (type.isInterface() && type.isAnnotationPresent(PublicType.class)) {
            throw invalidInterfaceAnnotation(targetType, type);
        }
        Deque<Class<?>> stack = new ArrayDeque<>();
        Collections.addAll(stack, type.getInterfaces());
        while (!stack.isEmpty()) {
            Class<?> current = stack.pop();
            if (current.isAnnotationPresent(PublicType.class)) {
                throw invalidInterfaceAnnotation(targetType, current);
            }
            Collections.addAll(stack, current.getInterfaces());
        }
    }

    private static PublicTypeSupplier newPublicTypeSupplierFor(Class<?> targetType, Class<?> annotatedType, Class<? extends PublicTypeSupplier> supplierType) {
        try {
            return supplierType.getConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw invalidPublicTypeSupplier(targetType, annotatedType, supplierType, ex);
        }
    }

    private static InvalidUserCodeException invalidAnnotationParameters(Class<?> targetType, Class<?> annotatedType) {
        return new InvalidUserCodeException("Public type detection for " + targetType + " failed: @PublicType annotation on " + annotatedType + " must not provide both `type` and `supplier` parameters.");
    }

    private static InvalidUserCodeException invalidInterfaceAnnotation(Class<?> targetType, Class<?> iface) {
        return new InvalidUserCodeException("Public type detection for " + targetType + " failed: @PublicType annotation should only be used on classes, not on " + iface + ".");
    }

    private static InvalidUserCodeException invalidPublicTypeSupplier(Class<?> targetType, Class<?> annotatedType, Class<?> supplierType, ReflectiveOperationException ex) {
        return new InvalidUserCodeException("Public type detection for " + targetType + " failed: @PublicType annotation on " + annotatedType + " has supplier " + supplierType + " which could not be instantiated, a public default constructor is required.", ex);
    }
}
