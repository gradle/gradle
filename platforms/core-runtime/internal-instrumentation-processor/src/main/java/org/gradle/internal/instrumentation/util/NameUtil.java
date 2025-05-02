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

package org.gradle.internal.instrumentation.util;

import com.google.common.base.Strings;
import com.squareup.javapoet.ClassName;
import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.objectweb.asm.Type;

import java.util.Locale;
import java.util.regex.Pattern;

public class NameUtil {

    private static final Pattern UPPER_CASE = Pattern.compile("(?=\\p{Upper})");

    private NameUtil() {
    }

    public static String getterName(String propertyName, Type propertyType) {
        String prefix = propertyType.equals(Type.BOOLEAN_TYPE) || propertyType.getClassName().equals("java.lang.Boolean") ? "is" : "get";
        return prefix + capitalize(propertyName);
    }

    public static String setterName(String propertyName) {
        return "set" + capitalize(propertyName);
    }

    public static String capitalize(String value) {
        return Strings.isNullOrEmpty(value)
            ? value
            : Character.toTitleCase(value.charAt(0)) + value.substring(1);
    }

    public static String camelToUpperUnderscoreCase(String camelCase) {
        String[] split = UPPER_CASE.split(camelCase);
        for (int i = 0; i < split.length; i++) {
            split[i] = split[i].toUpperCase(Locale.US);
        }
        return String.join("_", split);
    }

    public static String interceptedJvmMethodName(CallableInfo callableInfo) {
        if (callableInfo.getKind() == CallableKindInfo.GROOVY_PROPERTY_GETTER) {
            return getterName(callableInfo.getCallableName(), callableInfo.getReturnType().getType());
        }
        if (callableInfo.getKind() == CallableKindInfo.GROOVY_PROPERTY_SETTER) {
            return setterName(callableInfo.getCallableName());
        }
        return callableInfo.getCallableName();
    }

    /**
     * ClassName that correctly resolves name for classes that starts with $
     */
    public static ClassName getClassName(String fullClassName) {
        String[] splitted = fullClassName.split("[.]");
        String className = splitted[splitted.length - 1];
        String packageName = fullClassName.replace("." + className, "");
        return ClassName.get(packageName, className);
    }
}
