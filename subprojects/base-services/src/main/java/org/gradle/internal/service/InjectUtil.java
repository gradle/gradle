/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.service;

import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

final class InjectUtil {

    /**
     * Selects the single injectable constructor for the given type.
     * The type must either have only one public or package-private default constructor,
     * or it should have a single non-private constructor annotated with {@literal @}{@link Inject}.
     *
     * @param type the type to find the injectable constructor of.
     */
    static Constructor<?> selectConstructor(final Class<?> type) {
        if (isInnerClass(type)) {
            // The DI system doesn't support injecting non-static inner classes.
            throw new ServiceValidationException(
                String.format(
                    "Unable to select constructor for non-static inner class %s.",
                    format(type)
                )
            );
        }

        final Constructor<?>[] constructors = type.getDeclaredConstructors();
        if (constructors.length == 1) {
            Constructor<?> constructor = constructors[0];
            if (isPublicOrPackageScoped(constructor)) {
                // If there is a single constructor, and that constructor is public or package private we select it.
                return constructor;
            }
            if (constructor.getAnnotation(Inject.class) != null) {
                // Otherwise, if there is a single constructor that is annotated with `@Inject`, we select it (short-circuit).
                return constructor;
            }
        }

        // Search for a valid `@Inject` constructor to use instead.
        Constructor<?> match = null;
        for (Constructor<?> constructor : constructors) {
            if (constructor.getAnnotation(Inject.class) != null) {
                if (match != null) {
                    // There was a previously found a match. This means a second constructor with `@Inject` has been found.
                    throw new ServiceValidationException(
                        String.format(
                            "Multiple constructor annotated with @Inject for %s.",
                            format(type)
                        )
                    );
                }
                // A valid match was found.
                match = constructor;
            }
        }
        if (match == null) {
            // No constructor annotated with `@Inject` was found.
            throw new ServiceValidationException(
                String.format(
                    "Expected a single non-private constructor, or one constructor annotated with @Inject for %s.",
                    format(type)
                )
            );
        }

        return match;
    }

    private static boolean isInnerClass(Class<?> clazz) {
        return clazz.isMemberClass() && !Modifier.isStatic(clazz.getModifiers());
    }

    private static boolean isPublicOrPackageScoped(Constructor<?> constructor) {
        return Modifier.isPublic(constructor.getModifiers()) || isPackagePrivate(constructor.getModifiers());
    }

    static boolean isPackagePrivate(int modifiers) {
        return !Modifier.isPrivate(modifiers) && !Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers);
    }

    private static String format(Type type) {
        return TypeStringFormatter.format(type);
    }

    private InjectUtil() {
        /* no-op */
    }
}
