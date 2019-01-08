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

import org.gradle.internal.UncheckedException;

import java.lang.reflect.Constructor;

public class JavaReflectionUtil {

    public static Class<?> getWrapperTypeForPrimitiveType(Class<?> type) {
        if (type == Character.TYPE) {
            return Character.class;
        } else if (type == Boolean.TYPE) {
            return Boolean.class;
        } else if (type == Long.TYPE) {
            return Long.class;
        } else if (type == Integer.TYPE) {
            return Integer.class;
        } else if (type == Short.TYPE) {
            return Short.class;
        } else if (type == Byte.TYPE) {
            return Byte.class;
        } else if (type == Float.TYPE) {
            return Float.class;
        } else if (type == Double.TYPE) {
            return Double.class;
        }
        throw new IllegalArgumentException(String.format("Don't know the wrapper type for primitive type %s.", type));
    }

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
}
