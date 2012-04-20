/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.util;

import org.gradle.internal.UncheckedException;

import java.lang.reflect.Method;

/**
 * Simple implementations of some reflection capabilities. In contrast to {@link ReflectionUtil},
 * this class doesn't make use of Groovy.
 */
public class JavaReflectionUtil {
    public static Object readProperty(Object target, String property) {
        try {
            Method getterMethod;
            try {
                getterMethod = target.getClass().getMethod(toMethodName("get", property));
            } catch (NoSuchMethodException e) {
                try {
                    getterMethod = target.getClass().getMethod(toMethodName("is", property));
                } catch (NoSuchMethodException e2) {
                    throw e;
                }
            }
            return getterMethod.invoke(target);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static void writeProperty(Object target, String property, Object value) {
        try {
            String setterName = toMethodName("set", property);
            for (Method method: target.getClass().getMethods()) {
                if (!method.getName().equals(setterName)) { continue; }
                if (method.getParameterTypes().length != 1) { continue; }
                method.invoke(target, value);
                return;
            }
            throw new NoSuchMethodException(String.format("could not find setter method '%s'", setterName));
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static String toMethodName(String prefix, String propertyName) {
        return prefix + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    }
}
