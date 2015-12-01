/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema.extract;

import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Method;

/**
 * Distinguish get getters, is getters and setters from non-property methods.
 */
public enum MethodType {
    IS_GETTER, GET_GETTER, SETTER, NON_PROPERTY;

    public String propertyNameFor(Method method) {
        String methodName = method.getName();
        int prefixLength = this == MethodType.IS_GETTER ? 2 : 3;
        String methodNamePrefixRemoved = methodName.substring(prefixLength);
        return StringUtils.uncapitalize(methodNamePrefixRemoved);
    }

    public static MethodType of(Method method) {
        String methodName = method.getName();
        if (!hasVoidReturnType(method) && takesNoParameter(method)) {
            if (isGetGetterName(methodName)) {
                return GET_GETTER;
            }
            if (isIsGetterName(methodName)) {
                return IS_GETTER;
            }
        }
        if (hasVoidReturnType(method) && takesSingleParameter(method) && isSetterName(methodName)) {
            return SETTER;
        }
        return NON_PROPERTY;
    }

    public static boolean hasVoidReturnType(Method method) {
        return void.class.equals(method.getReturnType());
    }

    public static boolean takesNoParameter(Method method) {
        return method.getParameterTypes().length == 0;
    }

    public static boolean takesSingleParameter(Method method) {
        return method.getParameterTypes().length == 1;
    }

    public static boolean isPropertyMethodName(String methodName) {
        return isGetGetterName(methodName) || isIsGetterName(methodName) || isSetterName(methodName);
    }

    public static boolean isGetterName(String methodName) {
        return isGetGetterName(methodName) || isIsGetterName(methodName);
    }

    public static boolean isGetGetterName(String methodName) {
        return methodName.startsWith("get") && !"get".equals(methodName) && isNthCharUpperCase(methodName, 4);
    }

    public static boolean isIsGetterName(String methodName) {
        return methodName.startsWith("is") && !"is".equals(methodName) && isNthCharUpperCase(methodName, 3);
    }

    public static boolean isSetterName(String methodName) {
        return methodName.startsWith("set") && !"set".equals(methodName) && isNthCharUpperCase(methodName, 4);
    }

    private static boolean isNthCharUpperCase(String methodName, int position) {
        return Character.isUpperCase(methodName.charAt(position - 1));
    }
}
