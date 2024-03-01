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

import org.gradle.internal.instrumentation.model.CallableInfo;
import org.gradle.internal.instrumentation.model.CallableKindInfo;
import org.gradle.util.internal.TextUtil;
import org.objectweb.asm.Type;

public class NameUtil {
    private NameUtil() {
    }

    public static String getterName(String propertyName, Type propertyType) {
        String prefix = propertyType.equals(Type.BOOLEAN_TYPE) || propertyType.getClassName().equals("java.lang.Boolean") ? "is" : "get";
        return prefix + TextUtil.capitalize(propertyName);
    }

    public static String setterName(String propertyName) {
        return "set" + TextUtil.capitalize(propertyName);
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
}
