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

package org.gradle.api.internal.tasks.testing.assertion.mappers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AssertionMapper {

    protected static boolean isClassOrSubclass(String className, Class<?> cls) {
        if (className.equals(cls.getName())) {
            return true;
        }

        Class<?> superclass = cls.getSuperclass();
        if (superclass == null) {
            return false;
        } else {
            return isClassOrSubclass(className, superclass);
        }
    }

    protected static <T> T invokeMethod(Object obj, String methodName, Class<T> targetClass) throws Exception {
        Method method = obj.getClass().getDeclaredMethod(methodName);
        return targetClass.cast(method.invoke(obj));
    }

}
