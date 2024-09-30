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

package org.gradle.internal.reflect;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.reflect.TypeToken;
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;

public class JavaReflectionUtil {

    /**
     * This is intended to be a equivalent of deprecated {@link Class#newInstance()}.
     */
    public static <T> T newInstance(Class<T> c) {
        try {
            Constructor<T> constructor = c.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Returns the {@link TypeToken} of a parameter specified by index.
     *
     * @param beanType the TypeToken of the bean
     * @param parameterizedClass the parameterized class
     * @param typeParameterIndex the index of the parameter
     * @return a TypeToken
     */
    public static <T> TypeToken<?> extractNestedType(TypeToken<T> beanType, Class<? super T> parameterizedClass, int typeParameterIndex) {
        ParameterizedType type = (ParameterizedType) beanType.getSupertype(parameterizedClass).getType();
        return TypeToken.of(type.getActualTypeArguments()[typeParameterIndex]);
    }

    /**
     * Determine if the given class has the given annotation, honoring {@link Inherited} annotations
     * for superclasses and interfaces.
     */
    public static boolean hasAnnotation(Class<?> type, Class<? extends Annotation> annotationType) {
        return getAnnotation(type, annotationType, true) != null;
    }

    /**
     * Similar to {@link Class#getAnnotation(Class)}, except it also honors {@link Inherited} annotations
     * for interfaces.
     * <p>
     * Prefer {@link #hasAnnotation(Class, Class)} since this method will only return the _first_ annotation
     * found in a superclass or superinterface. Therefore, this method may be ambiguous if the annotation
     * is present in multiple superclasses or superinterfaces.
     */
    @Nullable
    @VisibleForTesting
    static <A extends Annotation> A getAnnotation(Class<?> type, Class<A> annotationType) {
        return getAnnotation(type, annotationType, true);
    }

    @Nullable
    private static <A extends Annotation> A getAnnotation(Class<?> type, Class<A> annotationType, boolean checkType) {
        A annotation;
        if (checkType) {
            annotation = type.getAnnotation(annotationType);
            if (annotation != null) {
                return annotation;
            }
        }

        if (annotationType.getAnnotation(Inherited.class) != null) {
            for (Class<?> anInterface : type.getInterfaces()) {
                annotation = getAnnotation(anInterface, annotationType, true);
                if (annotation != null) {
                    return annotation;
                }
            }
        }

        if (type.isInterface() || type.equals(Object.class)) {
            return null;
        } else {
            return getAnnotation(type.getSuperclass(), annotationType, false);
        }
    }
}
